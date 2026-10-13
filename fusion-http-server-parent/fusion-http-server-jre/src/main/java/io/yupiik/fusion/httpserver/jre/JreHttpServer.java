/*
 * Copyright (c) 2022 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.fusion.httpserver.jre;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.spi.Endpoint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Runs a list of fusion {@link Endpoint}s on top of the JDK
 * {@link HttpServer}. Endpoints are matched by priority order (highest first);
 * the first {@link Endpoint#matches(Request)} wins, otherwise a 404 is
 * returned.
 *
 * <p>Dispatch is fully non-blocking: the {@code HttpServer} context handler
 * hands the exchange to an {@link ExecutorService} (a virtual-thread-per-task
 * executor by default) and only writes the response once the endpoint's
 * {@code CompletionStage<Response>} completes. Streaming bodies are written
 * chunk-per-item on the same executor, so a slow consumer never pins the
 * server's accept thread.</p>
 */
public class JreHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final JreHttpServerConfiguration configuration;
    private final List<Endpoint> endpoints;
    private final ExecutorService executor;
    private final CountDownLatch closed = new CountDownLatch(1);
    private final AtomicBoolean started = new AtomicBoolean();

    public JreHttpServer(final JreHttpServerConfiguration configuration, final List<Endpoint> endpoints) {
        this.configuration = configuration;
        this.endpoints = endpoints.stream()
                .sorted(Comparator.comparingInt(Endpoint::priority).reversed())
                .toList();
        this.executor = configuration.executor() != null
                ? configuration.executor()
                : customExecutor(configuration.virtualThreads());
        try {
            this.server = HttpServer.create(new InetSocketAddress(configuration.host(), configuration.port()), 0);
        } catch (final IOException e) {
            throw new IllegalStateException(
                    "cannot bind the JRE HTTP server on " + configuration.host() + ":" + configuration.port(), e);
        }
        this.server.setExecutor(this.executor);
        this.server.createContext("/", this::dispatch);
    }

    public JreHttpServerConfiguration configuration() {
        return configuration;
    }

    /** The actual bound port (useful when configured with port {@code 0}). */
    public int port() {
        return server.getAddress().getPort();
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            server.start();
        }
    }

    /** Blocks until {@link #close()} is called. */
    public void await() throws InterruptedException {
        closed.await();
    }

    @Override
    public void close() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        // stop(0) blocks until every in-flight exchange completes, which can hang a JVM shutdown hook
        // forever on an SSE stream that never completes (a parked Flow.Subscriber waiting for a frame).
        // Use a short grace so genuine exchanges finish, then force-abort any stuck one.
        server.stop(SHUTDOWN_GRACE_SECONDS);
        if (executor == configuration.executor()) {
            executor.shutdownNow();
        }
        closed.countDown();
    }

    /**
     * Builds the default executor: virtual-thread-per-task when requested and the JDK supports it
     * (Java 21+), otherwise a plain cached thread pool with named platform threads (Java 17 fallback).
     *
     * @param virtualThreads whether virtual threads are preferred.
     * @return the executor to use.
     */
    private static ExecutorService customExecutor(final boolean virtualThreads) {
        if (virtualThreads) {
            try {
                final var method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
                final var executor = method.invoke(null);
                if (executor instanceof ExecutorService service) {
                    return service;
                }
            } catch (final ReflectiveOperationException | RuntimeException ignored) {
                // fall through to the platform thread pool
            }
        }
        final var counter = new AtomicInteger();
        return Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable runnable) {
                final var thread = new Thread(runnable, "jre-http-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    /** Grace period (seconds) given to in-flight exchanges to complete before {@code stop()} aborts them. */
    private static final int SHUTDOWN_GRACE_SECONDS = 2;

    private void dispatch(final HttpExchange exchange) {
        final var request = new JreRequest(exchange);
        final Endpoint matched;
        try {
            matched = endpoints.stream()
                    .filter(e -> e.matches(request))
                    .findFirst()
                    .orElse(null);
        } catch (final Throwable t) {
            write(
                    exchange,
                    Response.of()
                            .status(500)
                            .body("matching failed: " + t.getMessage())
                            .build(),
                    request);
            exchange.close();
            return;
        }
        if (matched == null) {
            write(exchange, Response.of().status(404).body("Not Found").build(), request);
            exchange.close();
            return;
        }
        matched.handle(request)
                .whenComplete((response, error) -> acceptor(exchange, request).accept(response, error));
    }

    private BiConsumer<? super Response, ? super Throwable> acceptor(
            final HttpExchange exchange, final Request request) {
        return (response, error) -> {
            final var resolved = error != null
                    ? Response.of()
                            .status(500)
                            .body("internal error: " + error.getMessage())
                            .build()
                    : response == null ? Response.of().status(204).build() : response;
            // A streaming (SSE) response must stay open: its body publisher pushes frames
            // asynchronously after this handler returns, so the exchange is closed by the
            // stream's completion/error callbacks instead of here. Closing it right away (as the
            // previous unconditional finally did) tore the stream down after the first frame,
            // which surfaced in the browser as an SSE connection that never delivers any later
            // event ("not connected").
            final var streaming = error == null && isStreaming(resolved);
            try {
                write(exchange, resolved, request);
            } finally {
                if (!streaming) {
                    exchange.close();
                }
            }
        };
    }

    private void write(final HttpExchange exchange, final Response response, final Request request) {
        final var headers = exchange.getResponseHeaders();
        response.headers().forEach((name, values) -> {
            if (values != null) {
                for (final var value : values) {
                    headers.add(name, value);
                }
            }
        });
        for (final var cookie : response.cookies()) {
            headers.add("set-cookie", serialize(cookie));
        }
        if (response.headers().get("content-type") == null) {
            headers.set("content-type", "application/octet-stream");
        }

        final var head = "HEAD".equalsIgnoreCase(request.method());
        if (head) {
            try {
                exchange.sendResponseHeaders(response.status(), -1);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return;
        }

        final var body = response.body();
        if (body == null) {
            try {
                exchange.sendResponseHeaders(response.status(), -1);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            return;
        }
        // SSE streams unbounded and must flush per event; any other body is aggregated
        // first so the Content-Length is known (the JRE server then sends it in one shot).
        final var sse = isStreaming(response);
        if (sse) {
            writeStreaming(exchange, response, request);
            return;
        }
        final var aggregated = new ByteArrayOutputStream();
        final var subscriptionFail = new Throwable[1];
        final var completed = new CompletableFuture<Void>();
        body.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final ByteBuffer item) {
                final var bytes = new byte[item.remaining()];
                item.get(bytes);
                aggregated.writeBytes(bytes);
            }

            @Override
            public void onError(final Throwable throwable) {
                subscriptionFail[0] = throwable;
                completed.complete(null);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }
        });
        completed.join();
        try {
            if (subscriptionFail[0] != null) {
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            final var payload = aggregated.toByteArray();
            exchange.sendResponseHeaders(response.status(), payload.length);
            if (payload.length > 0 && !"HEAD".equalsIgnoreCase(request.method())) {
                exchange.getResponseBody().write(payload);
                exchange.getResponseBody().close();
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void writeStreaming(final HttpExchange exchange, final Response response, final Request request) {
        try {
            exchange.sendResponseHeaders(response.status(), 0);
            final var output = exchange.getResponseBody();
            final var subscriptionRef = new Flow.Subscription[1];
            bodySubscribe(exchange, response.body(), subscriptionRef, output, new AtomicBoolean());
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void bodySubscribe(
            final HttpExchange exchange,
            final Flow.Publisher<ByteBuffer> body,
            final Flow.Subscription[] subscriptionRef,
            final OutputStream output,
            final AtomicBoolean errored) {
        body.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscriptionRef[0] = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(final ByteBuffer item) {
                try {
                    final var bytes = new byte[item.remaining()];
                    item.get(bytes);
                    output.write(bytes);
                    output.flush();
                } catch (final IOException e) {
                    onError(e);
                    return;
                }
                subscriptionRef[0].request(1);
            }

            @Override
            public void onError(final Throwable throwable) {
                errored.set(true);
                if (subscriptionRef[0] != null) {
                    subscriptionRef[0].cancel();
                }
                closeOutput(output);
                exchange.close();
            }

            @Override
            public void onComplete() {
                if (subscriptionRef[0] != null) {
                    subscriptionRef[0].cancel();
                }
                closeOutput(output);
                exchange.close();
            }
        });
    }

    private static void closeOutput(final OutputStream output) {
        try {
            output.close();
        } catch (final IOException ignored) {
            // ignore
        }
    }

    private static boolean isStreaming(final Response response) {
        final var contentTypes = response.headers().get("content-type");
        if (contentTypes == null) {
            return false;
        }
        for (final var value : contentTypes) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith("text/event-stream")) {
                return true;
            }
        }
        return false;
    }

    private static String serialize(final Cookie cookie) {
        final var sb = new StringBuilder();
        sb.append(cookie.name()).append('=').append(cookie.value() == null ? "" : cookie.value());
        if (cookie.path() != null) {
            sb.append("; Path=").append(cookie.path());
        }
        if (cookie.domain() != null) {
            sb.append("; Domain=").append(cookie.domain());
        }
        if (cookie.maxAge() >= 0) {
            sb.append("; Max-Age=").append(cookie.maxAge());
        }
        if (cookie.secure()) {
            sb.append("; Secure");
        }
        if (cookie.httpOnly()) {
            sb.append("; HttpOnly");
        }
        return sb.toString();
    }
}
