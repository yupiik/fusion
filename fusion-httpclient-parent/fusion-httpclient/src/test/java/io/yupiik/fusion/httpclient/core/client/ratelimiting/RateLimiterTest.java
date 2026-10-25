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

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimiterTest {
    private static Clock clockAt(final AtomicReference<Instant> instant) {
        return new Clock() {
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
    }

    @Test
    void rateLimit() {
        final var instant = new AtomicReference<>(Instant.ofEpochMilli(0));
        final var rateLimiter = new RateLimiter(3, 1_000, clockAt(instant));

        assertEquals(0, rateLimiter.before());
        assertEquals(0, rateLimiter.before());
        assertEquals(0, rateLimiter.before());
        assertEquals(1000, rateLimiter.before()); // next window is 1000ms away
        instant.set(Instant.ofEpochMilli(1000));

        assertEquals(1000, rateLimiter.before()); // next window (2000) is 1000ms away
        assertEquals(1000, rateLimiter.before());
        assertEquals(1000, rateLimiter.before());
        instant.set(Instant.ofEpochMilli(3000));

        assertEquals(0, rateLimiter.before());
        assertEquals(0, rateLimiter.before());
        assertEquals(0, rateLimiter.before());
    }

    @Test
    void perHostIsolatesBudgets() {
        final var rateLimiter = new RateLimiter(2, 1_000, clockAt(new AtomicReference<>(Instant.ofEpochMilli(0))), RateLimiter.Scope.PER_HOST);

        // two authorities each get their own budget: exhausting "a" does not throttle "b"
        assertEquals(0, rateLimiter.before("host-a"));
        assertEquals(0, rateLimiter.before("host-a"));
        assertEquals(1000, rateLimiter.before("host-a")); // host-a exhausted
        assertEquals(0, rateLimiter.before("host-b")); // host-b still fresh
        assertEquals(0, rateLimiter.before("host-b"));
    }

    @Test
    void globalSharesBudgetAcrossKeys() {
        final var rateLimiter = new RateLimiter(2, 1_000, clockAt(new AtomicReference<>(Instant.ofEpochMilli(0))), RateLimiter.Scope.GLOBAL);

        // GLOBAL ignores the key: all authorities share one budget
        assertEquals(0, rateLimiter.before("host-a"));
        assertEquals(0, rateLimiter.before("host-a"));
        assertEquals(1000, rateLimiter.before("host-a")); // shared budget exhausted
        assertEquals(1000, rateLimiter.before("host-b")); // host-b throttled by host-a's usage
    }

    @Test
    void perHostAllowsDifferentPermitsPerKey() {
        final var rateLimiter = new RateLimiter(
                key -> key.endsWith(":fast") ? 5 : 1,
                1_000, clockAt(new AtomicReference<>(Instant.ofEpochMilli(0))), RateLimiter.Scope.PER_HOST);

        // ":fast" gets 5 permits per window, ":slow" gets 1
        for (int i = 0; i < 5; i++) {
            assertEquals(0, rateLimiter.before("host:fast"));
        }
        assertEquals(1000, rateLimiter.before("host:fast")); // 6th :fast is throttled

        assertEquals(0, rateLimiter.before("host:slow")); // first :slow passes
        assertEquals(1000, rateLimiter.before("host:slow")); // second :slow is throttled at 1
    }

    @Test
    void perHostAllowsUnlimitedBudgetPerKey() {
        final var rateLimiter = new RateLimiter(
                key -> "host:unlimited".equals(key) ? Integer.MAX_VALUE : 1,
                1_000, clockAt(new AtomicReference<>(Instant.ofEpochMilli(0))), RateLimiter.Scope.PER_HOST);

        // ":unlimited" is never throttled, ":limited" is capped at 1
        for (int i = 0; i < 100; i++) {
            assertEquals(0, rateLimiter.before("host:unlimited"));
        }
        assertEquals(0, rateLimiter.before("host:limited"));
        assertEquals(1000, rateLimiter.before("host:limited"));
    }
}
