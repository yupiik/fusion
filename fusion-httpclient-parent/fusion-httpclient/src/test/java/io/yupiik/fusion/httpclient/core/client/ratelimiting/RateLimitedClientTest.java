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
package io.yupiik.fusion.httpclient.core.client.ratelimiting;

import io.yupiik.fusion.httpclient.core.DelegatingHttpClient;
import io.yupiik.fusion.httpclient.core.response.StaticHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitedClientTest {
    private static final long NOW = Instant.parse("2026-08-16T00:00:00Z").toEpochMilli();

    @Test
    void serverRateLimiting() throws ExecutionException, InterruptedException {
        assertPause(Map.of("Rate-Limit-Reset", "1"), 429, 1000);
    }

    @Test
    void handlesAllRetryHeaderSemantics() throws ExecutionException, InterruptedException {
        assertPause(Map.of("RateLimit-Reset", "1"), 429, 1000); // relative seconds
        assertPause(Map.of("X-Rate-Limit-Reset", "3"), 429, 3000); // relative seconds
        assertPause(Map.of("Retry-After", "2"), 429, 2000); // RFC 7231 delay-seconds
        assertPause(Map.of("Retry-After", httpDate(NOW + 5_000)), 429, 5000); // RFC 7231 HTTP-date
        assertPause(Map.of("X-Rate-Limit-Reset-Ms", String.valueOf(NOW + 3_000)), 429, 3000); // epoch millis
        assertPause(Map.of("X-Rate-Limit-Reset-Ms", "1500"), 429, 1500); // relative millis
        assertPause(Map.of("X-Rate-Limit-Reset", String.valueOf((NOW + 3_000) / 1000)), 429, 3000); // epoch seconds
        assertPause(Map.of("Retry-After", "1"), 403, 1000); // 403 with Retry-After is rate limited
        assertPause(Map.of("X-Rate-Limit-Reset", "not-a-number"), 429, 1000); // unparseable -> window fallback
    }

    private void assertPause(final Map<String, String> headers, final int status, final long expectedPause)
            throws ExecutionException, InterruptedException {
        final var instant = new AtomicReference<>(Instant.ofEpochMilli(NOW));
        final var clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(final ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return instant.get();
            }
        };

        final var counter = new AtomicInteger(0);
        final var pauses = new ArrayList<Long>();
        final var client = new RateLimitedClient(
                new DelegatingHttpClient(null) {
                    @Override
                    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                            final HttpRequest request,
                            final HttpResponse.BodyHandler<T> responseBodyHandler) {
                        final var throttled = counter.getAndIncrement() == 0;
                        return completedFuture(new StaticHttpResponse<T>(
                                request, Version.HTTP_1_1,
                                throttled ? status : 200,
                                HttpHeaders.of(throttled ? toHeaderMap(headers) : Map.of(), (a, b) -> true),
                                null));
                    }
                },
                new RateLimiter(Integer.MAX_VALUE, Integer.MAX_VALUE, clock),
                1000, clock) {
            @Override
            protected <T> void doDelay(final long pause, final Callable<CompletableFuture<HttpResponse<T>>> delayedExecution) {
                try {
                    delayedExecution.call();
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            protected <T> CompletableFuture<HttpResponse<T>> doScheduleLater(final long pause, final HttpRequest request, final Supplier<CompletableFuture<HttpResponse<T>>> promise, final int retry) {
                pauses.add(pause);
                instant.set(Instant.ofEpochMilli(instant.get().toEpochMilli() + pause));
                return super.doScheduleLater(pause, request, promise, retry);
            }
        };
        assertEquals(
                200,
                client.sendAsync(
                                HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:1234")).build(), discarding())
                        .get()
                        .statusCode());
        assertEquals(List.of(expectedPause), pauses);
    }

    @Test
    void clientSideThrottleDefersOverflowRequestByOneWindow() throws ExecutionException, InterruptedException, TimeoutException {
        final var instant = new AtomicReference<Instant>(Instant.ofEpochMilli(NOW));
        final var clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(final ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return instant.get();
            }
        };

        final var sent = new AtomicInteger(0);
        final var scheduled = new AtomicInteger(0);
        final var client = new RateLimitedClient(
                new DelegatingHttpClient(null) {
                    @Override
                    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                            final HttpRequest request,
                            final HttpResponse.BodyHandler<T> responseBodyHandler) {
                        sent.incrementAndGet();
                        return completedFuture(new StaticHttpResponse<T>(
                                request, Version.HTTP_1_1, 200, HttpHeaders.of(Map.of(), (a, b) -> true), null));
                    }
                },
                new RateLimiter(2, 1000, clock),
                1000, clock) {
            @Override
            protected <T> CompletableFuture<HttpResponse<T>> doScheduleLater(final long pause, final HttpRequest request, final Supplier<CompletableFuture<HttpResponse<T>>> promise, final int retry) {
                scheduled.incrementAndGet();
                instant.set(Instant.ofEpochMilli(instant.get().toEpochMilli() + pause));
                return super.doScheduleLater(pause, request, promise, retry);
            }

            @Override
            protected <T> void doDelay(final long pause, final Callable<CompletableFuture<HttpResponse<T>>> delayedExecution) {
                try {
                    delayedExecution.call();
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        try {
            final var request = HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:1234")).build();
            final var responses = java.util.stream.IntStream.range(0, 3)
                    .mapToObj(i -> client.sendAsync(request, discarding()))
                    .toList();
            for (final var response : responses) {
                assertEquals(200, response.get(20, SECONDS).statusCode());
            }
            assertEquals(3, sent.get());
            assertEquals(1, scheduled.get()); // one window hop, not a compounding chain
        } finally {
            client.close();
        }
    }

    @Test
    void completesFacadeWhenScheduledResponseIsAlsoRateLimited() throws ExecutionException, InterruptedException, TimeoutException {
        final var clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(final ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(NOW);
            }
        };

        // call 0: 429, call 1: 429 again (inside scheduled task), call 2: 200
        final var counter = new AtomicInteger(0);
        final var client = new RateLimitedClient(
                new DelegatingHttpClient(null) {
                    @Override
                    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                            final HttpRequest request,
                            final HttpResponse.BodyHandler<T> responseBodyHandler) {
                        final var n = counter.getAndIncrement();
                        final var status = n < 2 ? 429 : 200;
                        final var headers = n < 2
                                ? Map.of("Retry-After", List.of("1"))
                                : Map.<String, List<String>>of();
                        return completedFuture(new StaticHttpResponse<>(
                                request, Version.HTTP_1_1,
                                status,
                                HttpHeaders.of(headers, (a, b) -> true),
                                null));
                    }
                },
                new RateLimiter(Integer.MAX_VALUE, Integer.MAX_VALUE, clock),
                1000, clock);

        try {
            final var response = client.sendAsync(
                            HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:1234")).build(), discarding())
                    .get(20, SECONDS);
            assertEquals(200, response.statusCode());
        } finally {
            client.close();
        }
    }

    @Test
    void asyncBareRateLimitedIsBoundedByMaxRetries() throws InterruptedException {
        final var clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(final ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(NOW);
            }
        };

        // a bare 429 (no retry headers) uses the window fallback, so without a bound the async
        // path would recurse forever; the fix must complete exceptionally after maxRetries.
        final int maxRetries = 2;
        final var calls = new AtomicInteger(0);
        final var client = new RateLimitedClient(
                new DelegatingHttpClient(null) {
                    @Override
                    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                            final HttpRequest request,
                            final HttpResponse.BodyHandler<T> responseBodyHandler) {
                        calls.incrementAndGet();
                        return completedFuture(new StaticHttpResponse<T>(
                                request, Version.HTTP_1_1, 429, HttpHeaders.of(Map.of(), (a, b) -> true), null));
                    }
                },
                new RateLimiter(Integer.MAX_VALUE, Integer.MAX_VALUE, clock),
                1000, clock, maxRetries) {
            @Override
            protected <T> void doDelay(final long pause, final Callable<CompletableFuture<HttpResponse<T>>> delayedExecution) {
                try {
                    delayedExecution.call();
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        try {
            final var request = HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:1234")).build();
            final var future = client.sendAsync(request, discarding());
            final var failure = assertThrows(ExecutionException.class, future::get);
            assertEquals(IllegalStateException.class, failure.getCause().getClass());
            assertTrue(calls.get() <= maxRetries + 1, "expected at most " + (maxRetries + 1) + " calls but got " + calls.get());
        } finally {
            client.close();
        }
    }

    @Test
    void scheduledSurrogateIsAlsoBoundedByMaxRetries() throws InterruptedException {
        final var clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(final ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(NOW);
            }
        };

        // force an initial client-side throttle then keep getting 429 with a Retry-After,
        // so the bound must hold across doScheduleLater's internal recursion too.
        final int maxRetries = 3;
        final var calls = new AtomicInteger(0);
        final var client = new RateLimitedClient(
                new DelegatingHttpClient(null) {
                    @Override
                    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                            final HttpRequest request,
                            final HttpResponse.BodyHandler<T> responseBodyHandler) {
                        calls.incrementAndGet();
                        return completedFuture(new StaticHttpResponse<T>(
                                request, Version.HTTP_1_1, 429,
                                HttpHeaders.of(Map.of("Retry-After", List.of("1")), (a, b) -> true), null));
                    }
                },
                new RateLimiter(Integer.MAX_VALUE, Integer.MAX_VALUE, clock) {
                    @Override
                    public long before() {
                        return 1000; // always throttle, driving the scheduled path
                    }
                },
                1000, clock, maxRetries) {
            @Override
            protected <T> void doDelay(final long pause, final Callable<CompletableFuture<HttpResponse<T>>> delayedExecution) {
                try {
                    delayedExecution.call();
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        try {
            final var request = HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:1234")).build();
            final var future = client.sendAsync(request, discarding());
            final var failure = assertThrows(ExecutionException.class, future::get);
            assertEquals(IllegalStateException.class, failure.getCause().getClass());
            assertTrue(calls.get() <= maxRetries + 1, "expected at most " + (maxRetries + 1) + " calls but got " + calls.get());
        } finally {
            client.close();
        }
    }

    @Test
    void perHostBudgetIsolatedAcrossAuthorities() throws ExecutionException, InterruptedException {
        final var clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(final ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(NOW);
            }
        };

        // one shared RateLimitedClient with a PER_HOST limiter: exhausting host-a must
        // not throttle host-b (in GLOBAL mode host-b would share host-a's budget).
        final var sent = new AtomicInteger(0);
        final var scheduled = new AtomicInteger(0);
        final var hosts = new ArrayList<String>();
        final var client = new RateLimitedClient(
                new DelegatingHttpClient(null) {
                    @Override
                    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                            final HttpRequest request,
                            final HttpResponse.BodyHandler<T> responseBodyHandler) {
                        sent.incrementAndGet();
                        hosts.add(request.uri().getHost());
                        return completedFuture(new StaticHttpResponse<T>(
                                request, Version.HTTP_1_1, 200, HttpHeaders.of(Map.of(), (a, b) -> true), null));
                    }
                },
                new RateLimiter(1, 1000, clock, RateLimiter.Scope.PER_HOST),
                1000, clock) {
            @Override
            protected <T> CompletableFuture<HttpResponse<T>> doScheduleLater(final long pause, final HttpRequest request,
                                                                             final Supplier<CompletableFuture<HttpResponse<T>>> promise,
                                                                             final int retry) {
                scheduled.incrementAndGet();
                return super.doScheduleLater(pause, request, promise, retry);
            }

            @Override
            protected <T> void doDelay(final long pause, final Callable<CompletableFuture<HttpResponse<T>>> delayedExecution) {
                try {
                    delayedExecution.call();
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        try {
            final var hostA = HttpRequest.newBuilder().GET().uri(URI.create("http://host-a:80/")).build();
            final var hostB = HttpRequest.newBuilder().GET().uri(URI.create("http://host-b:80/")).build();

            assertEquals(200, client.sendAsync(hostA, discarding()).get().statusCode()); // host-a consumes its only permit
            assertEquals(200, client.sendAsync(hostA, discarding()).get().statusCode()); // host-a throttled -> scheduled
            assertEquals(200, client.sendAsync(hostB, discarding()).get().statusCode()); // host-b fresh -> not throttled

            assertEquals(3, sent.get());
            assertEquals(1, scheduled.get()); // only host-a's second request was throttled
            assertTrue(hosts.contains("host-a"));
            assertTrue(hosts.contains("host-b"));
        } finally {
            client.close();
        }
    }

    private String httpDate(final long epochMilli) {
        return DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O", Locale.ENGLISH)
                .format(OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC));
    }

    private Map<String, List<String>> toHeaderMap(final Map<String, String> headers) {
        return headers.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
    }
}
