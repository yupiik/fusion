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
import java.util.concurrent.Semaphore;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Throttles exchanges, this can be useful to control the load on a backend but also
 * enforce to respect the HTTP/2.0 max concurrent stream value (even with a single connection).
 */
public class ThrottledHttpClient extends DelegatingHttpClient {
    private final Semaphore semaphore;
    private final Queue<CompletableFuture<Void>> waitingQueue = new ConcurrentLinkedQueue<>();
    private final boolean onlyHttp2;

    public ThrottledHttpClient(final HttpClient delegate, final Semaphore semaphore, final boolean onlyHttp2) {
        super(delegate);
        this.onlyHttp2 = onlyHttp2;
        this.semaphore = semaphore;
    }

    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        if (!shouldThrottle(request)) {
            return super.send(request, responseBodyHandler);
        }
        semaphore.acquire();
        try {
            return super.send(request, responseBodyHandler);
        } finally {
            release();
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                            final HttpResponse.BodyHandler<T> responseBodyHandler,
                                                            final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        if (!shouldThrottle(request)) {
            return super.sendAsync(request, responseBodyHandler, pushPromiseHandler);
        }

        return acquireAsync()
                .thenCompose(v -> super.sendAsync(request, responseBodyHandler, pushPromiseHandler))
                .whenComplete((ok, ko) -> release());
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                            final HttpResponse.BodyHandler<T> responseBodyHandler) {
        if (!shouldThrottle(request)) {
            return super.sendAsync(request, responseBodyHandler);
        }

        return acquireAsync()
                .thenCompose(v -> super.sendAsync(request, responseBodyHandler))
                .whenComplete((ok, ko) -> release());
    }

    private boolean shouldThrottle(final HttpRequest request) {
        return !onlyHttp2 || request
                .version()
                .map(it -> it == Version.HTTP_2)
                .orElse(true);
    }

    private CompletableFuture<Void> acquireAsync() {
        if (semaphore.tryAcquire()) {
            return completedFuture(null);
        }

        final var future = new CompletableFuture<Void>();
        waitingQueue.offer(future);
        // need to recheck to avoid the case
        // where we put it in the queue, release() was called
        // so it would just hang
        if (!future.isDone() && semaphore.tryAcquire()) {
            if (waitingQueue.remove(future)) {
                future.complete(null);
            } else {
                semaphore.release();
            }
        }
        return future;
    }

    private void release() {
        CompletableFuture<Void> waiting;
        if ((waiting = waitingQueue.poll()) != null) {
            waiting.complete(null);
        } else {
            semaphore.release();
        }
    }
}
