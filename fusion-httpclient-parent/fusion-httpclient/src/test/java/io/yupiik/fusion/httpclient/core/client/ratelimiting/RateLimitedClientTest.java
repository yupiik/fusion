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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
            protected <T> CompletableFuture<HttpResponse<T>> doScheduleLater(final long pause, final HttpRequest request, final Supplier<CompletableFuture<HttpResponse<T>>> promise) {
                pauses.add(pause);
                instant.set(Instant.ofEpochMilli(instant.get().toEpochMilli() + pause));
                return super.doScheduleLater(pause, request, promise);
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

    private String httpDate(final long epochMilli) {
        return DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O", Locale.ENGLISH)
                .format(OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneOffset.UTC));
    }

    private Map<String, List<String>> toHeaderMap(final Map<String, String> headers) {
        return headers.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
    }
}
