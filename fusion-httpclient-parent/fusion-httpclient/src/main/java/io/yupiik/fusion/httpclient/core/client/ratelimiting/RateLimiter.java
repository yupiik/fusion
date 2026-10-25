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

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static java.util.stream.Collectors.toSet;

public class RateLimiter {
    /**
     * The granularity of the rate-limit budget.
     */
    public enum Scope {
        /** One shared budget across all authorities (the historical behavior). */
        GLOBAL,
        /** An independent budget per request authority (typically {@code host:port}). */
        PER_HOST
    }

    private static final String DEFAULT_KEY = "*";

    private final Function<String, Integer> permits;
    private final int window;
    private final Clock clock;
    private final Scope scope;
    private final ConcurrentMap<String, ConcurrentMap<Long, AtomicLong>> counterPerKeyAndWindow = new ConcurrentHashMap<>();
    private final AtomicBoolean cleaning = new AtomicBoolean();

    public RateLimiter(final int rateLimiterPermits, final int rateLimiterWindow, final Clock clock) {
        this(constantPermits(rateLimiterPermits), rateLimiterWindow, clock, Scope.GLOBAL);
    }

    public RateLimiter(final int rateLimiterPermits, final int rateLimiterWindow, final Clock clock, final Scope scope) {
        this(constantPermits(rateLimiterPermits), rateLimiterWindow, clock, scope);
    }

    /**
     * Creates a rate limiter whose maximum permit count can vary per request key
     * (typically a host authority with {@link Scope#PER_HOST}).
     * The function receives the effective key (the authority, collapsed to a default
     * key in {@link Scope#GLOBAL} mode) and must return the maximum permits for that
     * key in a window, or {@link Integer#MAX_VALUE} to leave it unlimited.
     * It must be thread-safe as it is invoked concurrently for every request.
     *
     * @param rateLimiterPermits the per-key permit resolver
     * @param rateLimiterWindow the rate-limit window in milliseconds
     * @param clock the clock used to delimit windows
     * @param scope the budget granularity
     */
    public RateLimiter(final Function<String, Integer> rateLimiterPermits, final int rateLimiterWindow, final Clock clock, final Scope scope) {
        this.permits = Objects.requireNonNull(rateLimiterPermits);
        this.window = rateLimiterWindow;
        this.clock = clock;
        this.scope = scope == null ? Scope.GLOBAL : scope;
    }

    private static Function<String, Integer> constantPermits(final int permits) {
        return key -> permits;
    }

    public long before() {
        return before(DEFAULT_KEY);
    }

    /**
     * Accounts one request for a given authority and returns the delay to wait
     * before sending it (0 = send now).
     * With {@link Scope#GLOBAL} the {@code key} is ignored and a single budget is
     * shared; with {@link Scope#PER_HOST} the budget depends on {@code key}.
     *
     * @param key the request authority, or {@code null} to use the default key
     * @return the delay in milliseconds before the request may be sent
     */
    public long before(final String key) {
        final String effectiveKey = scope == Scope.PER_HOST ? (key == null ? DEFAULT_KEY : key) : DEFAULT_KEY;
        final int permitsForKey = permits.apply(effectiveKey);
        if (permitsForKey == Integer.MAX_VALUE) { // "disabled" for this key
            return 0;
        }

        final long now = clock.instant().toEpochMilli();
        final long windowKey = now / window;
        final var windows = counterPerKeyAndWindow.computeIfAbsent(effectiveKey, k -> new ConcurrentHashMap<>());

        final var previousCounter = windows.computeIfAbsent(windowKey - 1, k -> new AtomicLong());
        final var counter = windows.computeIfAbsent(windowKey, k -> new AtomicLong());
        final var windowCounter = counter.incrementAndGet();
        final var previousCounterValue = previousCounter.get();
        if (previousCounterValue == 0) {
            if (windowCounter <= permitsForKey) {
                return 0;
            }
            return nextWindow(windowKey, now);
        }

        final double weightInWindow = (now - windowKey * window) * 1. / window;
        final double count = previousCounterValue * (1 - weightInWindow) + windowCounter;
        if ((windows.size() > 60 || counterPerKeyAndWindow.size() > 10) && cleaning.compareAndSet(false, true)) { // don't do it for each request
            try {
                if (windows.size() > 60) {
                    cleanBuckets(windows, windowKey);
                }
                if (counterPerKeyAndWindow.size() > 10) {
                    cleanHosts(windowKey);
                }
            } finally {
                cleaning.set(false);
            }
        }

        if (count <= permitsForKey) {
            return 0;
        }
        return nextWindow(windowKey, now);
    }

    private long nextWindow(final long key, final long now) {
        return Math.max(0, (key * window) + window - now);
    }

    public void after() {
        // no-op in this impl, hook if we update the algorithm
    }

    /**
     * Releases the accounting for a given authority, symmetric with {@link #before(String)}.
     * Currently a no-op hook kept for symmetry with a future algorithm.
     *
     * @param key the request authority, or {@code null} to use the default key
     */
    public void after(final String key) {
        // no-op in this impl, kept symmetric with before(String) for a future algorithm
        after();
    }

    private void cleanBuckets(final ConcurrentMap<Long, AtomicLong> windows, final long windowKey) { // control memory usage, nothing more
        final var keys = windows.keySet();
        if (keys.size() > 10) {
            final var cleanedUp = keys.stream()
                    .filter(l -> l < windowKey - 2)
                    .collect(toSet());
            if (!cleanedUp.isEmpty()) {
                keys.removeAll(cleanedUp);
            }
        }
    }

    private void cleanHosts(final long windowKey) { // drop hosts not contacted for 2 windows
        counterPerKeyAndWindow.entrySet().removeIf(entry -> {
            final var windows = entry.getValue();
            return windows.keySet().stream().noneMatch(l -> l >= windowKey - 2);
        });
    }
}
