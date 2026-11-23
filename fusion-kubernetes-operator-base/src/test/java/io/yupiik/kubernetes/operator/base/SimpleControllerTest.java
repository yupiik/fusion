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

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.kubernetes.operator.base.impl.FusionGeneratedModule;
import io.yupiik.kubernetes.operator.base.impl.ObjectLike;
import io.yupiik.kubernetes.operator.base.impl.SimpleController;
import io.yupiik.kubernetes.operator.base.spi.Operator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
class SimpleControllerTest {
    @Test
    void legacyQueueIsRealAndSharesEventFifoWithOnEvent() throws Exception {
        try (final var fixture = new Fixture(1)) {
            final BlockingQueue<String> queue = fixture.controller.events(); // source compatibility
            queue.add(bookmark("removed"));
            assertEquals(bookmark("removed"), queue.peek());
            assertTrue(queue.remove(bookmark("removed")));
            queue.put(bookmark("one"));
            fixture.controller.onEvent(bookmark("two"));
            assertTrue(queue.offer(bookmark("three"), 1, SECONDS));
            queue.addAll(List.of(bookmark("four"), bookmark("five")));
            final var copied = new ArrayList<String>();
            assertEquals(1, queue.drainTo(copied, 1));
            assertEquals(List.of(bookmark("one")), copied);
            assertEquals(4, queue.toArray().length);
            fixture.controller.init();
            fixture.controller.stop();
            assertEquals(List.of("two", "three", "four", "five"), fixture.received);
            fixture.controller.onEvent(bookmark("rejected"));
            fixture.controller.task(() -> fixture.received.add("rejected-task"));
            fixture.controller.init();
            assertEquals(List.of("two", "three", "four", "five"), fixture.received);
            assertEquals(42, fixture.scheduler.submit(() -> 42).get(2, SECONDS));
            assertFalse(fixture.scheduler.isShutdown());
        }
    }

    @Test
    void idleLegacyWritesWakeAndNeitherLaneStarves() throws Exception {
        try (final var fixture = new Fixture(1)) {
            fixture.controller.init();
            final var warm = new CountDownLatch(1);
            fixture.controller.task(warm::countDown);
            assertTrue(warm.await(2, SECONDS));
            final var direct = new CountDownLatch(1);
            fixture.bookmarkCallback = version -> direct.countDown();
            fixture.controller.events().put(bookmark("idle"));
            assertTrue(direct.await(2, SECONDS));
            fixture.bookmarkCallback = version -> {};

            final var entered = new CountDownLatch(1);
            final var release = new CountDownLatch(1);
            fixture.controller.task(() -> await(entered, release));
            assertTrue(entered.await(2, SECONDS));
            fixture.received.clear();
            for (int i = 0; i < 100; i++) {
                final var index = i;
                fixture.controller.events().add(bookmark("event-" + i));
                fixture.controller.task(() -> fixture.received.add("task-" + index));
            }
            release.countDown();
            fixture.controller.stop();
            assertEquals(200, fixture.received.size());
            for (int i = 0; i < 100; i++) {
                assertEquals("event-" + i, fixture.received.get(2 * i));
                assertEquals("task-" + i, fixture.received.get(2 * i + 1));
            }
        }
    }

    @Test
    void ownedWorkersDoNotConsumeBorrowedSchedulerAndInitIsIdempotent() throws Exception {
        try (final var fixture = new Fixture(4)) {
            fixture.controller.init();
            fixture.controller.init();
            assertEquals(42, fixture.scheduler.schedule(() -> 42, 1, MILLISECONDS).get(2, SECONDS));
            final var entered = new CountDownLatch(4);
            final var release = new CountDownLatch(1);
            final var running = new AtomicInteger();
            for (int i = 0; i < 4; i++) {
                fixture.controller.task(() -> {
                    running.incrementAndGet();
                    await(entered, release);
                });
            }
            try {
                assertTrue(entered.await(2, SECONDS));
                final var fifth = new CountDownLatch(1);
                fixture.controller.task(fifth::countDown);
                assertFalse(fifth.await(100, MILLISECONDS));
                assertEquals(4, running.get());
                assertEquals(42, fixture.scheduler.submit(() -> 42).get(2, SECONDS));
            } finally {
                release.countDown();
            }
            fixture.controller.stop();
            assertFalse(fixture.scheduler.isShutdown());
        }
    }

    @Test
    void failuresDoNotKillWorkersAndContinuationsDrain() throws Exception {
        try (final var fixture = new Fixture(1)) {
            fixture.controller.events().add("not-json");
            fixture.controller.task(() -> { throw new IllegalStateException("expected"); });
            final var entered = new CountDownLatch(1);
            final var release = new CountDownLatch(1);
            fixture.controller.task(() -> {
                await(entered, release);
                fixture.controller.continuation(() -> fixture.received.add("continuation"));
            });
            fixture.controller.init();
            assertTrue(entered.await(2, SECONDS));
            fixture.controller.events().add(bookmark("pending"));
            final var stopper = Executors.newSingleThreadExecutor();
            try {
                final var stopped = stopper.submit(fixture.controller::stop);
                fixture.controller.awaitStopping();
                // onEvent acquires the admission lock, so its return also fences the snapshot.
                fixture.controller.onEvent(bookmark("rejected"));
                fixture.controller.events().add(bookmark("late-direct"));
                fixture.controller.task(() -> fixture.received.add("rejected-task"));
                release.countDown();
                stopped.get(2, SECONDS);
                assertEquals(List.of("pending", "continuation"), fixture.received);
            } finally {
                release.countDown();
                stopper.shutdownNow();
            }
        }
    }

    @Test
    void multipleWorkersDrainContinuationChainsAfterAdmissionCloses() throws Exception {
        try (final var fixture = new Fixture(4)) {
            final var entered = new CountDownLatch(4);
            final var release = new CountDownLatch(1);
            final var completed = new AtomicInteger();
            for (int i = 0; i < 4; i++) {
                fixture.controller.task(() -> {
                    await(entered, release);
                    fixture.controller.continuation(new Runnable() {
                        private int remaining = 64;

                        @Override
                        public void run() {
                            completed.incrementAndGet();
                            if (--remaining > 0) {
                                fixture.controller.continuation(this);
                            }
                        }
                    });
                });
            }
            fixture.controller.init();
            final var stopper = Executors.newSingleThreadExecutor();
            try {
                assertTrue(entered.await(2, SECONDS));
                final var stopped = stopper.submit(fixture.controller::stop);
                fixture.controller.awaitStopping();
                fixture.controller.onEvent(bookmark("rejected"));
                fixture.controller.task(() -> fixture.received.add("rejected-task"));
                release.countDown();
                stopped.get(2, SECONDS);
                assertEquals(256, completed.get());
                assertTrue(fixture.received.isEmpty());
            } finally {
                release.countDown();
                stopper.shutdownNow();
            }
        }
    }

    @Test
    void interruptedDrainRejectsContinuationsFromAStillRunningCallback() throws Exception {
        try (final var fixture = new Fixture(1)) {
            final var entered = new CountDownLatch(1);
            final var release = new CountDownLatch(1);
            final var completed = new CountDownLatch(1);
            fixture.controller.task(() -> {
                entered.countDown();
                // Model a callback which does not finish immediately when shutdown interrupts it.
                boolean released = false;
                while (!released) {
                    try {
                        release.await();
                        released = true;
                    } catch (final InterruptedException ignored) {
                        // The callback deliberately finishes only when the test releases it.
                    }
                }
                fixture.controller.continuation(() -> fixture.received.add("late-continuation"));
                completed.countDown();
            });
            fixture.controller.init();
            try {
                assertTrue(entered.await(2, SECONDS));
                try {
                    Thread.currentThread().interrupt();
                    fixture.controller.stop();
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
                release.countDown();
                assertTrue(completed.await(2, SECONDS));
                fixture.controller.stop(); // also joins the worker after the callback returns
                assertTrue(fixture.received.isEmpty());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void stopBoundsBlockedHandlerAndIgnoresLateLegacySubmissions() throws Exception {
        try (final var fixture = new Fixture(1)) {
            final var entered = new CountDownLatch(1);
            final var interrupted = new CountDownLatch(1);
            fixture.controller.task(() -> {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interrupted.countDown();
                }
            });
            fixture.controller.init();
            assertTrue(entered.await(2, SECONDS));
            fixture.controller.events().add(bookmark("pending"));
            final var start = System.nanoTime();
            fixture.controller.stop();
            final var elapsed = System.nanoTime() - start;
            assertTrue(elapsed >= SECONDS.toNanos(4));
            assertTrue(elapsed < SECONDS.toNanos(8));
            assertTrue(interrupted.await(2, SECONDS));
            fixture.controller.events().add(bookmark("late"));
            fixture.controller.stop();
            assertTrue(fixture.received.isEmpty());
            assertEquals(42, fixture.scheduler.submit(() -> 42).get(2, SECONDS));
        }
    }

    @Test
    void interruptedStopPreservesInterruptAndInterruptsOwnedWorker() throws Exception {
        try (final var fixture = new Fixture(1)) {
            final var entered = new CountDownLatch(1);
            final var interrupted = new CountDownLatch(1);
            fixture.controller.task(() -> {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (final InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            });
            fixture.controller.init();
            assertTrue(entered.await(2, SECONDS));
            try {
                Thread.currentThread().interrupt();
                fixture.controller.stop();
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertTrue(interrupted.await(2, SECONDS));
            assertFalse(fixture.scheduler.isShutdown());
        }
    }

    @Test
    void oldCompiledSubclassLinksToRestoredField(@TempDir final Path directory) throws Exception {
        // Compile against an independent old API shape, then load only the subclass against today's class.
        final var oldApi = directory.resolve("SimpleController.java");
        final var subclass = directory.resolve("LegacyController.java");
        Files.writeString(oldApi, """
                package io.yupiik.kubernetes.operator.base.impl;
                import java.util.concurrent.*;
                import io.yupiik.fusion.json.JsonMapper;
                import io.yupiik.kubernetes.operator.base.spi.Operator;
                public abstract class SimpleController<T extends ObjectLike> {
                    protected final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
                    public SimpleController(ScheduledExecutorService threads, JsonMapper mapper, int count, Operator<T> operator) {}
                }
                """);
        Files.writeString(subclass, """
                package legacy;
                import java.util.concurrent.*;
                import io.yupiik.fusion.json.JsonMapper;
                import io.yupiik.kubernetes.operator.base.impl.*;
                import io.yupiik.kubernetes.operator.base.spi.Operator;
                public class LegacyController extends SimpleController<ObjectLike> {
                    public LegacyController(ScheduledExecutorService threads, JsonMapper mapper, Operator<ObjectLike> operator) {
                        super(threads, mapper, 1, operator);
                    }
                    public void submit(String event) throws InterruptedException { queue.put(event); }
                }
                """);
        final var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "-proc:none", "--release", "17", "-classpath",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                "-d", directory.toString(), oldApi.toString(), subclass.toString()));
        try (final var fixture = new Fixture(1);
             final var loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, getClass().getClassLoader())) {
            final var type = loader.loadClass("legacy.LegacyController");
            final var controller = (SimpleController<?>) type.getConstructor(ScheduledExecutorService.class, JsonMapper.class, Operator.class)
                    .newInstance(fixture.scheduler, fixture.mapper, fixture.operator);
            try {
                type.getMethod("submit", String.class).invoke(controller, bookmark("linked"));
                controller.init();
            } finally {
                controller.stop();
            }
            assertEquals(List.of("linked"), fixture.received);
        }
    }

    private static String bookmark(final String value) {
        return "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"" + value + "\"}}}";
    }

    private static void await(final CountDownLatch entered, final CountDownLatch release) {
        entered.countDown();
        try {
            release.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Controller extends SimpleController<ObjectLike> {
        private Controller(final ScheduledExecutorService scheduler, final JsonMapper mapper, final int count,
                           final Operator<ObjectLike> operator) {
            super(scheduler, mapper, count, operator);
        }

        private BlockingQueue<String> events() {
            return queue;
        }

        private void task(final Runnable task) {
            enqueue(task);
        }

        private void continuation(final Runnable task) {
            enqueueContinuation(task);
        }

        private void awaitStopping() throws InterruptedException {
            final var deadline = System.nanoTime() + SECONDS.toNanos(2);
            while (!stopping && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            assertTrue(stopping);
        }
    }

    private static class Fixture implements AutoCloseable {
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        private final JsonMapperImpl mapper = new JsonMapperImpl(List.of(FusionGeneratedModule.Event$FusionJsonCodec()), key -> Optional.empty());
        private final List<String> received = new CopyOnWriteArrayList<>();
        private volatile Consumer<String> bookmarkCallback = version -> {};
        private final Operator<ObjectLike> operator = new Operator.Base<>(ObjectLike.class,
                new Operator.DefaultOperatorConfiguration(false, List.of(), "samples", "test/v1")) {
            @Override
            public void onBookmark(final String resourceVersion) {
                received.add(resourceVersion);
                bookmarkCallback.accept(resourceVersion);
            }
        };
        private final Controller controller;

        private Fixture(final int count) {
            controller = new Controller(scheduler, mapper, count, operator);
        }

        @Override
        public void close() {
            controller.stop();
            scheduler.shutdownNow();
            mapper.close();
        }
    }
}
