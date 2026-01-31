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

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class ThrottledHttpClientTest {
    @Test
    void http11Throttling() {
        try (final var server = new Server(ex -> {
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(1);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, false)) {
                final var response = throttled.sendAsync(
                        server.GET().version(HttpClient.Version.HTTP_1_1).build(),
                        HttpResponse.BodyHandlers.ofString()).join();
                assertEquals(200, response.statusCode());
                assertEventually(() -> assertEquals(1, semaphore.availablePermits()));
            }
        }
    }

    @Test
    @Timeout(60)
    void noHttp11Throttling() {
        try (final var server = new Server(ex -> {
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            // 0 permits so it hangs forever if throttling was applied
            final var semaphore = new Semaphore(0);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, true)) {
                final var response = throttled.sendAsync(
                                server.GET().version(HttpClient.Version.HTTP_1_1).build(),
                                HttpResponse.BodyHandlers.ofString())
                        .orTimeout(5, TimeUnit.SECONDS).join();
                assertEquals(200, response.statusCode());
                assertEquals(0, semaphore.availablePermits());
            }
        }
    }

    @Test
    void noVersionThrottling() {
        try (final var server = new Server(ex -> {
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(1);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, true)) {
                final var response = throttled.sendAsync(
                        server.GET().build(),
                        HttpResponse.BodyHandlers.ofString()).join();
                assertEquals(200, response.statusCode());
                assertEventually(() -> assertEquals(1, semaphore.availablePermits()));
            }
        }
    }

    @Test
    void immediateIfPermitAvailable() {
        try (final var server = new Server(ex -> {
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(5);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, false)) {

                final var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();
                for (int i = 0; i < 5; i++) {
                    futures.add(throttled.sendAsync(server.GET().build(), HttpResponse.BodyHandlers.ofString()));
                }
                futures.forEach(f -> f.orTimeout(5, TimeUnit.SECONDS).join());
                futures.forEach(f -> assertEquals(200, f.join().statusCode()));

                assertEventually(() -> assertEquals(5, semaphore.availablePermits()));
            }
        }
    }

    @Test
    void secondRequestUnblockedOnlyAfterFirstCompletes() throws Exception {
        final var serverHit = new AtomicInteger(0);
        final var firstRequestStarted = new CountDownLatch(1);
        final var firstRequestRelease = new CountDownLatch(1);

        try (final var server = new Server(ex -> {
            final int hit = serverHit.incrementAndGet();
            if (hit == 1) {
                firstRequestStarted.countDown();
                try {
                    firstRequestRelease.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(1);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, false)) {
                final var f1 = throttled.sendAsync(server.GET().build(), HttpResponse.BodyHandlers.ofString());
                assertTrue(firstRequestStarted.await(2, TimeUnit.SECONDS));

                final var f2 = throttled.sendAsync(server.GET().build(), HttpResponse.BodyHandlers.ofString());
                assertFalse(f2.isDone());
                assertEquals(1, serverHit.get());

                firstRequestRelease.countDown();
                f1.orTimeout(5, TimeUnit.SECONDS).join();
                f2.orTimeout(5, TimeUnit.SECONDS).join();

                assertEquals(200, f1.join().statusCode());
                assertEquals(200, f2.join().statusCode());
                assertEquals(2, serverHit.get());
            }
        }
    }

    @Test
    void waitersUnblockedInFIFOOrder() throws Exception {
        final var hitOrder = new CopyOnWriteArrayList<Integer>();
        final var serverHit = new AtomicInteger(0);
        final var firstStarted = new CountDownLatch(1);
        final var firstRelease = new CountDownLatch(1);

        try (final var server = new Server(ex -> {
            final int hit = serverHit.incrementAndGet();
            hitOrder.add(hit);
            if (hit == 1) {
                firstStarted.countDown();
                try {
                    firstRelease.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(1);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, false)) {

                final var f1 = throttled.sendAsync(server.GET().build(), HttpResponse.BodyHandlers.ofString());
                firstStarted.await(2, TimeUnit.SECONDS);

                final var f2 = throttled.sendAsync(server.GET().build(), HttpResponse.BodyHandlers.ofString());
                final var f3 = throttled.sendAsync(server.GET().build(), HttpResponse.BodyHandlers.ofString());
                final var f4 = throttled.sendAsync(server.GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(1, serverHit.get());

                firstRelease.countDown();
                f1.orTimeout(5, TimeUnit.SECONDS).join();
                f2.orTimeout(5, TimeUnit.SECONDS).join();
                f3.orTimeout(5, TimeUnit.SECONDS).join();
                f4.orTimeout(5, TimeUnit.SECONDS).join();

                assertEquals(List.of(1, 2, 3, 4), hitOrder);
                assertEventually(() -> assertEquals(1, semaphore.availablePermits()));
            }
        }
    }

    @Test
    void sendAcquiresAndReleasesPermit() throws InterruptedException, IOException {
        try (final var server = new Server(ex -> {
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(1);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, false)) {
                final var response = throttled.send(server.GET().build(), HttpResponse.BodyHandlers.ofString());

                assertEquals(200, response.statusCode());
                assertEquals(1, semaphore.availablePermits());
            }
        }
    }

    @Test
    void sendReleasesPermitOnServerError() throws InterruptedException, IOException {
        try (final var server = new Server(ex -> {
            ex.sendResponseHeaders(500, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(1);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, false)) {
                final var response = throttled.send(server.GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(500, response.statusCode());
                assertEquals(1, semaphore.availablePermits());
            }
        }
    }

    @Test
    void sendReleasesPermitOnConnectionFailure() {
        // point at a port with nothing listening
        final var deadUri = URI.create("http://localhost:1");
        final var semaphore = new Semaphore(1);
        try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
             final var throttled = new ThrottledHttpClient(client, semaphore, false)) {
            assertThrows(IOException.class, () ->
                    throttled.send(
                            HttpRequest.newBuilder().GET().uri(deadUri).build(),
                            HttpResponse.BodyHandlers.ofString()));

            assertEquals(1, semaphore.availablePermits());
        }
    }

    @Test
    void sendOnlyHttp2DoesNotThrottleHttp1() throws IOException, InterruptedException {
        try (final var server = new Server(ex -> {
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(0);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, true)) {
                final var response = throttled.send(
                        server.GET().version(HttpClient.Version.HTTP_1_1).build(),
                        HttpResponse.BodyHandlers.ofString());

                assertEquals(200, response.statusCode());
                assertEquals(0, semaphore.availablePermits());
            }
        }
    }

    @Test
    void peakConcurrencyNeverExceedsPermitCount() {
        final int maxConcurrency = 3;
        final int totalRequests = 30;
        final var currentConcurrency = new AtomicInteger(0);
        final var peakConcurrency = new AtomicInteger(0);

        try (final var server = new Server(ex -> {
            final int current = currentConcurrency.incrementAndGet();
            peakConcurrency.updateAndGet(peak -> Math.max(peak, current));
            try {
                Thread.sleep(10);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            currentConcurrency.decrementAndGet();
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(maxConcurrency);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, false)) {
                final var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();
                for (int i = 0; i < totalRequests; i++) {
                    futures.add(throttled.sendAsync(server.GET().build(), HttpResponse.BodyHandlers.ofString()));
                }
                futures.forEach(f -> f.orTimeout(10, TimeUnit.SECONDS).join());

                assertTrue(peakConcurrency.get() <= maxConcurrency,
                        "Peak concurrency was " + peakConcurrency.get() + " but max allowed is " + maxConcurrency);
                assertEventually(() -> assertEquals(maxConcurrency, semaphore.availablePermits()));
            }
        }
    }

    @Test
    void allPermitsRecoveredAfterBurstOfRequests() {
        final int maxConcurrency = 5;
        final int totalRequests = 50;

        try (final var server = new Server(ex -> {
            try {
                Thread.sleep(5);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ex.sendResponseHeaders(200, 0);
            ex.close();
        })) {
            final var semaphore = new Semaphore(maxConcurrency);
            try (final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration());
                 final var throttled = new ThrottledHttpClient(client, semaphore, false)) {

                final var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();
                for (int i = 0; i < totalRequests; i++) {
                    futures.add(throttled.sendAsync(server.GET().build(), HttpResponse.BodyHandlers.ofString()));
                }
                futures.forEach(f -> f.orTimeout(10, TimeUnit.SECONDS).join());

                assertEventually(() -> assertEquals(maxConcurrency, semaphore.availablePermits()));
            }
        }
    }

    private void assertEventually(final Runnable assertion) {
        final var deadline = System.currentTimeMillis() + 2_000;
        while (true) {
            try {
                assertion.run();
                return;
            } catch (final AssertionError e) {
                if (System.currentTimeMillis() >= deadline) {
                    throw e;
                }
                Thread.yield();
            }
        }
    }

    private static class Server implements AutoCloseable {
        private final HttpServer server;

        private Server(final HttpHandler httpHandler) {
            try {
                this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                this.server.createContext("/").setHandler(httpHandler);
                this.server.setExecutor(null);
                this.server.start();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private URI base() {
            return URI.create("http://localhost:" + server.getAddress().getPort());
        }

        private HttpRequest.Builder GET() {
            return HttpRequest.newBuilder().GET().uri(base());
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}