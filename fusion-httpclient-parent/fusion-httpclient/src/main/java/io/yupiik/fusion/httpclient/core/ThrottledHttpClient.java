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
package io.yupiik.fusion.httpclient.core;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Throttles exchanges, this can be useful to control the load on a backend but also
 * enforce to respect the HTTP/2.0 max concurrent stream value (even with a single connection).
 */
public class ThrottledHttpClient extends DelegatingHttpClient {
    private final AsyncSemaphore semaphore;
    private final Queue<CompletableFuture<Void>> waitingQueue = new ConcurrentLinkedQueue<>();
    private final boolean onlyHttp2;

    public ThrottledHttpClient(final HttpClient delegate, final int maxConcurrency, final boolean onlyHttp2) {
        super(delegate);
        this.onlyHttp2 = onlyHttp2;
        this.semaphore = new AsyncSemaphore(maxConcurrency);
    }

    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        if (!shouldThrottle(request)) {
            return super.send(request, responseBodyHandler);
        }
        try {
            semaphore.acquire().get();
        } catch (final ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        try {
            return super.send(request, responseBodyHandler);
        } finally {
            semaphore.release();
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                            final HttpResponse.BodyHandler<T> responseBodyHandler,
                                                            final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        if (!shouldThrottle(request)) {
            return super.sendAsync(request, responseBodyHandler, pushPromiseHandler);
        }

        return semaphore
                .acquire()
                .thenCompose(v -> super.sendAsync(request, responseBodyHandler, pushPromiseHandler))
                .whenComplete((ok, ko) -> semaphore.release());
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                            final HttpResponse.BodyHandler<T> responseBodyHandler) {
        if (!shouldThrottle(request)) {
            return super.sendAsync(request, responseBodyHandler);
        }

        return semaphore
                .acquire()
                .thenCompose(v -> super.sendAsync(request, responseBodyHandler))
                .whenComplete((ok, ko) -> semaphore.release());
    }

    private boolean shouldThrottle(final HttpRequest request) {
        return !onlyHttp2 || request
                .version()
                .map(it -> it == Version.HTTP_2)
                .orElse(true);
    }

    private static final class AsyncSemaphore {
        private int permits;
        private final Queue<CompletableFuture<Permit>> waiters = new ConcurrentLinkedQueue<>();

        private AsyncSemaphore(final int permits) {
            if (permits <= 0) {
                throw new IllegalArgumentException("permits <= 0: " + permits);
            }
            this.permits = permits;
        }

        private CompletableFuture<Permit> acquire() {
            CompletableFuture<Permit> cf;
            synchronized (this) {
                if (permits > 0) {
                    permits--;
                    return CompletableFuture.completedFuture(new Permit(this));
                }

                cf = new CompletableFuture<>();
            }
            waiters.add(cf);
            return cf;
        }

        private void release() {
            CompletableFuture<Permit> next;
            synchronized (this) {
                next = waiters.poll();
                if (next == null) {
                    permits++;
                    return;
                }
            }
            next.complete(new Permit(this));
        }

        private static final class Permit implements AutoCloseable {
            private final AsyncSemaphore semaphore;
            private final AtomicBoolean released = new AtomicBoolean(false);

            private Permit(final AsyncSemaphore semaphore) {
                this.semaphore = semaphore;
            }

            @Override
            public void close() {
                if (released.compareAndSet(false, true)) {
                    semaphore.release();
                }
            }
        }
    }
}
