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
package io.yupiik.kubernetes.operator.base;

import io.yupiik.kubernetes.operator.base.impl.Threads;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class ThreadsTest {
    @Test
    void closeDrainsAcceptedTasks() throws Exception {
        final var executor = Executors.newScheduledThreadPool(1);
        final var completed = new AtomicInteger();
        try (final var threads = new Threads(executor)) {
            executor.execute(completed::incrementAndGet);
            executor.execute(completed::incrementAndGet);
        }
        assertEquals(2, completed.get());
        assertTrue(executor.isTerminated());
    }

    @Test
    void interruptedCloseDoesNotExecutePendingTasksOnCaller() throws Exception {
        final var executor = Executors.newScheduledThreadPool(1);
        final var entered = new CountDownLatch(1);
        final var interrupted = new CountDownLatch(1);
        final var queued = new AtomicInteger();
        executor.execute(() -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (final InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(entered.await(2, SECONDS));
        executor.execute(queued::incrementAndGet);
        try {
            Thread.currentThread().interrupt();
            new Threads(executor).close();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
            executor.shutdownNow();
        }
        assertTrue(interrupted.await(2, SECONDS));
        assertTrue(executor.awaitTermination(2, SECONDS));
        assertEquals(0, queued.get());
    }
}
