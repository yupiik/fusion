/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.httpclient.core.client.ratelimiting;

import io.yupiik.fusion.httpclient.core.DelegatingHttpClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class RateLimitedClient extends DelegatingHttpClient {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final ReentrantLock lock = new ReentrantLock();

    private final RateLimiter clientRateLimiter;
    private final long window;
    private final Clock clock;

    private volatile ScheduledExecutorService scheduler;
    private volatile boolean stopped = false;

    public RateLimitedClient(final HttpClient delegate, final RateLimiter clientRateLimiter,
                             final long windowDuration, final Clock clock) {
        super(delegate);
        this.clientRateLimiter = clientRateLimiter;
        this.window = windowDuration;
        this.clock = clock;
    }

    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        final var pause = clientRateLimiter.before();
        try {
            if (pause > 0) {
                log(request, pause);
                Thread.sleep(pause);
                return send(request, responseBodyHandler);
            }
            final var res = super.send(request, responseBodyHandler);
            if (isRateLimited(res)) {
                Thread.sleep(findPause(res));
                return send(request, responseBodyHandler);
            }
            return res;
        } finally {
            clientRateLimiter.after();
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) {
        final var pause = clientRateLimiter.before();
        return wrap(pause, request, () -> super.sendAsync(request, responseBodyHandler));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler, final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        final var pause = clientRateLimiter.before();
        return wrap(pause, request, () -> super.sendAsync(request, responseBodyHandler, pushPromiseHandler));
    }

    private void log(final HttpRequest request, final long pause) {
        logger.warning(() -> "Rate limiting (client side) " + request.method() + " " + request.uri() + " for " + pause + "ms");
    }

    private <T> CompletableFuture<HttpResponse<T>> wrap(final long pause, final HttpRequest request,
                                                        final Supplier<CompletableFuture<HttpResponse<T>>> promise) {
        if (pause == 0) {
            return promise.get()
                    .whenComplete((ok, ko) -> clientRateLimiter.after())
                    .thenCompose(ok -> {
                        if (isRateLimited(ok)) {
                            final long newPause = findPause(ok);
                            return wrap(newPause, request, promise);
                        }
                        return completedFuture(ok);
                    });
        }
        return doScheduleLater(pause, request, promise);
    }

    protected <T> CompletableFuture<HttpResponse<T>> doScheduleLater(final long pause, final HttpRequest request,
                                                                     final Supplier<CompletableFuture<HttpResponse<T>>> promise) {
        log(request, pause);
        final var facade = new CompletableFuture<HttpResponse<T>>();
        final Callable<CompletableFuture<HttpResponse<T>>> delayedExecution = () -> wrap(
                clientRateLimiter.before(), request,
                () -> promise.get().whenComplete((ok, ko) -> {
                    try {
                        if (isRateLimited(ok)) {
                            final long newPause = findPause(ok);
                            wrap(newPause, request, promise);
                            return;
                        }
                        if (ko != null) {
                            facade.completeExceptionally(ko);
                        } else {
                            facade.complete(ok);
                        }
                    } finally {
                        clientRateLimiter.after();
                    }
                }));
        doDelay(pause, delayedExecution);
        clientRateLimiter.after();
        return facade;
    }

    protected <T> void doDelay(final long pause, final Callable<CompletableFuture<HttpResponse<T>>> delayedExecution) {
        scheduledExecutorService().schedule(delayedExecution, pause, MILLISECONDS);
    }

    private <T> long findPause(final HttpResponse<T> res) {
        final var headers = res.headers();
        return headers.firstValue("Retry-After")
                .flatMap(a -> {
                    try { // RFC7231
                        return Optional.of(OffsetDateTime.parse(a.strip(), DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")));
                    } catch (final RuntimeException re2) {
                        return Optional.empty();
                    }
                })
                .map(d -> Math.max(0, d.toInstant().toEpochMilli() - clock.millis()))
                .or(() -> headers.firstValue("X-Rate-Limit-Reset-Ms")
                        .map(Long::parseLong))
                .or(() -> headers.firstValue("X-Rate-Limit-Reset")
                        .map(it -> TimeUnit.SECONDS.toMillis(Long.parseLong(it))))
                .or(() -> headers.firstValue("Rate-Limit-Reset")
                        .map(it -> TimeUnit.SECONDS.toMillis(Long.parseLong(it))))
                .orElse(window);
    }

    private <T> boolean isRateLimited(final HttpResponse<T> ok) {
        return ok.statusCode() == 429;
    }

    private ScheduledExecutorService scheduledExecutorService() { // lazy to avoid to create it if never needed
        if (!stopped && scheduler == null) {
            lock.lock();
            try {
                if (!stopped && scheduler == null) {
                    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                        final var thread = new Thread(r, RateLimitedClient.class.getName());
                        thread.setContextClassLoader(RateLimitedClient.class.getClassLoader());
                        return thread;
                    });
                }
            } finally {
                lock.unlock();
            }
        }
        return scheduler;
    }

    @Override
    public void close() throws Exception {
        stopped = true;
        final var ref = scheduler;
        if (ref != null) {
            ref.shutdownNow();
        }
        super.close();
    }
}
