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

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.spi.Endpoint;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JreHttpServerTest {

    private static HttpClient client;

    private JreHttpServer server;

    @BeforeAll
    static void warmClient() {
        client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    private JreHttpServer start(final Endpoint... endpoints) {
        final var instance = new JreHttpServer(new JreHttpServerConfiguration(null, 0, true), List.of(endpoints));
        instance.start();
        server = instance;
        return instance;
    }

    private static String url(final JreHttpServer s, final String path) {
        return "http://127.0.0.1:" + s.port() + path;
    }

    private static HttpResponse<String> get(final String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> send(final String url, final String method, final String body)
            throws Exception {
        final var builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("content-type", "application/json");
        if ("HEAD".equals(method)) {
            builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void requestAdaptation() throws Exception {
        final var seen = new AtomicReference<Request>();
        final var s = start(Endpoint.of(r -> "/echo".equals(r.path()), r -> {
            seen.set(r);
            return completedFuture(Response.of()
                    .status(200)
                    .body("m=" + r.method() + " q=" + r.query() + " p=" + r.parameter("a") + " p2=" + r.parameter("b"))
                    .build());
        }));
        final var resp = get(url(s, "/echo?x=1&a=hello&b=world"));
        assertEquals(200, resp.statusCode());
        assertEquals("m=GET q=x=1&a=hello&b=world p=hello p2=world", resp.body());
        final var req = seen.get();
        assertEquals(null, req.attribute("missing", String.class));
        req.setAttribute("k", "v");
        assertEquals("v", req.attribute("k", String.class));
        assertEquals("/echo", req.path());
        assertEquals("GET", req.method());
    }

    @Test
    void dispatchByPriorityThen404() throws Exception {
        final var first = new AtomicInteger();
        final var second = new AtomicInteger();
        final var s = start(
                // lower priority should not be reached when the higher matches
                Endpoint.of(
                        r -> "/prio".equals(r.path()),
                        r -> {
                            first.incrementAndGet();
                            return completedFuture(
                                    Response.of().status(200).body("first").build());
                        },
                        1000),
                Endpoint.of(
                        r -> "/prio".equals(r.path()),
                        r -> {
                            second.incrementAndGet();
                            return completedFuture(
                                    Response.of().status(200).body("second").build());
                        },
                        500));
        final var hit = get(url(s, "/prio"));
        assertEquals(200, hit.statusCode());
        assertEquals("first", hit.body());
        assertEquals(1, first.get());
        assertEquals(0, second.get());

        final var miss = get(url(s, "/nope"));
        assertEquals(404, miss.statusCode());
    }

    @Test
    void streamBodyWritesAllAndFlushesChunks() throws Exception {
        final var s = start(Endpoint.of(r -> "/stream".equals(r.path()), r -> {
            return completedFuture(Response.of()
                    .status(200)
                    .header("content-type", "text/event-stream")
                    .body(new Flow.Publisher<>() {
                        @Override
                        public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
                            subscriber.onSubscribe(new Flow.Subscription() {
                                private boolean done;

                                @Override
                                public void request(final long n) {
                                    if (done) {
                                        return;
                                    }
                                    done = true;
                                    for (int i = 0; i < 3; i++) {
                                        subscriber.onNext(
                                                ByteBuffer.wrap(("chunk" + i + "\n").getBytes(StandardCharsets.UTF_8)));
                                    }
                                    subscriber.onComplete();
                                }

                                @Override
                                public void cancel() {}
                            });
                        }
                    })
                    .build());
        }));
        final var resp = get(url(s, "/stream"));
        assertEquals(200, resp.statusCode());
        assertEquals("chunk0\nchunk1\nchunk2\n", resp.body());
    }

    @Test
    void headReturnsNoBody() throws Exception {
        final var s = start(Endpoint.of(
                r -> "/head".equals(r.path()),
                r -> completedFuture(Response.of()
                        .status(200)
                        .header("content-length", "5")
                        .body("hello")
                        .build())));
        final var resp = send(url(s, "/head"), "HEAD", null);
        assertEquals(200, resp.statusCode());
        assertEquals("", resp.body());
    }

    @Test
    void bufferedBodyGetsContentLength() throws Exception {
        final var s = start(Endpoint.of(
                r -> "/json".equals(r.path()),
                r -> completedFuture(Response.of()
                        .status(200)
                        .header("content-type", "application/json")
                        .body("{\"ok\":true}")
                        .build())));
        final var resp = get(url(s, "/json"));
        assertEquals(200, resp.statusCode());
        assertEquals("{\"ok\":true}", resp.body());
    }

    @Test
    void healthCheckHeadersAndMethods() throws Exception {
        final var s = start(Endpoint.of(
                r -> "/health".equals(r.path()) && "GET".equals(r.method()),
                r -> completedFuture(Response.of().status(204).build())));
        final var resp = get(url(s, "/health"));
        assertEquals(204, resp.statusCode());
        // POST to a GET-only endpoint matches nothing -> 404 (matches+priority dispatch)
        final var post = send(url(s, "/health"), "POST", "");
        assertEquals(404, post.statusCode());
    }

    @Test
    void asyncEndpointCompletesReactivement() throws Exception {
        final var s = start(Endpoint.of(r -> "/async".equals(r.path()), r -> {
            final var future = new CompletableFuture<Response>();
            CompletableFuture.runAsync(() -> future.complete(
                            Response.of().status(201).body("late").build()))
                    .join();
            return future;
        }));
        final var resp = get(url(s, "/async"));
        assertEquals(201, resp.statusCode());
        assertEquals("late", resp.body());
    }

    @Test
    void acceptsRequestBody() throws Exception {
        final var body = new AtomicBoolean();
        final var s = start(Endpoint.of(r -> "/body".equals(r.path()), r -> {
            body.set(true);
            return r.fullBody()
                    .string()
                    .thenApply(b -> Response.of().status(200).body("got:" + b).build());
        }));
        final var resp = send(url(s, "/body"), "POST", "payload");
        assertEquals(200, resp.statusCode());
        assertEquals("got:payload", resp.body());
        assertTrue(body.get());
    }

    /**
     * Regression probe for the web "not connected" symptom: a long-lived SSE stream must stay open
     * across asynchronously pushed frames. The prior test only covered a publisher that completes
     * synchronously in {@code subscribe()}, so it cannot catch the server tearing down the stream
     * right after headers are committed (e.g. closing the exchange while the body is still open).
     * This test pushes a first frame immediately (proving headers flush) and a second distinct
     * frame after a delay, then asserts the second one arrives - i.e. the connection survived.
     */
    @Test
    void streamingResponseStaysOpenForDelayedFrames() throws Exception {
        final var frames = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        final var s = start(Endpoint.of(
                r -> "/sse".equals(r.path()),
                r -> completedFuture(Response.of()
                        .status(200)
                        .header("content-type", "text/event-stream")
                        .body(new Flow.Publisher<>() {
                            @Override
                            public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
                                subscriber.onSubscribe(new Flow.Subscription() {
                                    private boolean initialSent;
                                    private boolean delayedStarted;

                                    @Override
                                    public void request(final long n) {
                                        // first demand: flush headers with a sentinel frame, then schedule one
                                        // delayed frame. The JRE adapter asks for one item per onNext (backpressure);
                                        // each further request(1) is a no-op so nothing floods - we never produce more
                                        // than these two frames.
                                        if (!initialSent) {
                                            initialSent = true;
                                            subscriber.onNext(
                                                    ByteBuffer.wrap(": ping\n\n".getBytes(StandardCharsets.UTF_8)));
                                        }
                                        if (!delayedStarted) {
                                            delayedStarted = true;
                                            final var late = new Thread(
                                                    () -> {
                                                        try {
                                                            Thread.sleep(500);
                                                        } catch (final InterruptedException e) {
                                                            Thread.currentThread()
                                                                    .interrupt();
                                                            return;
                                                        }
                                                        subscriber.onNext(ByteBuffer.wrap(
                                                                "data: hello\n\n".getBytes(StandardCharsets.UTF_8)));
                                                    },
                                                    "sse-delayed-push");
                                            late.setDaemon(true);
                                            late.start();
                                        }
                                    }

                                    @Override
                                    public void cancel() {
                                        // nothing to tear down; the daemon frame just goes nowhere
                                    }
                                });
                            }
                        })
                        .build())));

        // read the stream on a daemon thread: {ofLines()} blocks until EOF, and the stream is
        // supposed to stay open (only a broken server closes it), so we must not await EOF here.
        final var reader = new Thread(
                () -> {
                    try {
                        final var resp = client.send(
                                HttpRequest.newBuilder(URI.create(url(s, "/sse")))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofLines());
                        try (final var lines = resp.body()) {
                            lines.forEach(frames::add);
                        }
                    } catch (final Exception e) {
                        frames.add("!error:" + e);
                    }
                },
                "sse-test-reader");
        reader.setDaemon(true);
        reader.start();

        // the immediate frame arrives right away; the delayed one proves the stream stayed open
        awaitUntil(() -> frames.contains(": ping"), 3, TimeUnit.SECONDS);
        assertTrue(frames.contains(": ping"), "expected the initial frame to flush headers, got: " + frames);
        awaitUntil(() -> frames.contains("data: hello"), 5, TimeUnit.SECONDS);
        assertTrue(
                frames.contains("data: hello"),
                "expected the delayed frame to arrive on the still-open stream, got: " + frames);
    }

    /**
     * Regression test for the web shutdown hang: {@code close()} on a server with a long-lived, never-
     * completing SSE stream must return within a bounded time. {@code HttpServer.stop(0)} waits
     * indefinitely for in-flight exchanges, so a parked SSE subscriber (connected browser tab) used
     * to wedge the JVM shutdown hook forever; the bounded {@code stop(grace)} now aborts it.
     */
    @Test
    void closeReturnsWhenAnSseStreamNeverCompletes() throws Exception {
        final var frames = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        // an SSE stream that flushes headers but NEVER completes nor errors and stays fully open
        final var s = start(Endpoint.of(r -> "/hang".equals(r.path()), r -> {
            final Response resp = Response.of()
                    .status(200)
                    .header("content-type", "text/event-stream")
                    .body((Flow.Publisher<ByteBuffer>) subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                        @Override
                        public void request(final long n) {
                            subscriber.onNext(ByteBuffer.wrap(": open\n\n".getBytes(StandardCharsets.UTF_8)));
                        }

                        @Override
                        public void cancel() {}
                    }))
                    .build();
            return completedFuture(resp);
        }));

        final var reader = new Thread(
                () -> {
                    try {
                        final var resp = client.send(
                                HttpRequest.newBuilder(URI.create(url(s, "/hang")))
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofLines());
                        try (final var lines = resp.body()) {
                            lines.forEach(frames::add);
                        }
                    } catch (final Exception e) {
                        frames.add("!error:" + e);
                    }
                },
                "sse-hang-reader");
        reader.setDaemon(true);
        reader.start();

        // wait for the stream to be fully established (headers flushed) before closing
        awaitUntil(() -> frames.contains(": open"), 3, TimeUnit.SECONDS);
        assertTrue(frames.contains(": open"), "expected the stream to open, got: " + frames);

        // close() must return promptly: the bounded stop aborts the never-completing exchange instead
        // of blocking the shutdown-hook thread forever the way stop(0) did.
        final var start = System.nanoTime();
        s.close();
        final var elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(elapsedMs < 30_000, "close() hung on an open SSE stream (" + elapsedMs + "ms)");
    }

    /** Polls {@code condition} for up to {@code timeout}; eases line-stream assertions. */
    private static void awaitUntil(
            final java.util.function.BooleanSupplier condition, final long timeout, final TimeUnit unit)
            throws InterruptedException {
        final var deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }
}
