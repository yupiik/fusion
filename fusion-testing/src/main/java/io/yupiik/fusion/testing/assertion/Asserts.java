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
package io.yupiik.fusion.testing.assertion;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class Asserts {
    private Asserts() {
        // no-op
    }

    public static void waitUntil(final BooleanSupplier predicate) {
        try {
            until(predicate, 75, 60_000, Clock.systemUTC(), null).toCompletableFuture().get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static CompletionStage<?> until(final BooleanSupplier predicate,
                                           final long retryPauseMs,
                                           final long maxMs,
                                           final Clock clock,
                                           final ScheduledExecutorService es) {
        final var scheduler = es == null ? Executors.newScheduledThreadPool(1, r -> {
            final var thread = new Thread(r, Asserts.class.getName() + "-waituntil-" + Math.abs(predicate.hashCode()));
            thread.setContextClassLoader(predicate.getClass().getClassLoader());
            return thread;
        }) : es;
        final var time = clock == null ? Clock.systemUTC() : clock;
        final var end = clock.instant().plusMillis(maxMs);
        final var result = new CompletableFuture<Void>();
        doWaitUntil(predicate, retryPauseMs, time, end, result, scheduler);
        if (es == null) {
            result.whenComplete((ok, ko) -> scheduler.shutdownNow().forEach(Runnable::run));
        }
        return result;
    }

    private static void doWaitUntil(final BooleanSupplier predicate,
                                    final long retryPauseMs,
                                    final Clock time,
                                    final Instant end,
                                    final CompletableFuture<Void> result,
                                    final ScheduledExecutorService scheduler) {
        if (predicate.getAsBoolean()) {
            result.complete(null);
            return;
        }
        if (time.instant().isAfter(end)) {
            result.completeExceptionally(new TimeoutException("Timeout"));
        } else {
            scheduler.schedule(() -> doWaitUntil(predicate, retryPauseMs, time, end, result, scheduler), retryPauseMs, MILLISECONDS);
        }
    }
}
