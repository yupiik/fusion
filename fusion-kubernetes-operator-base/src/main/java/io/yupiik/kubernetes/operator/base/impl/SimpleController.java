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
package io.yupiik.kubernetes.operator.base.impl;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.kubernetes.operator.base.spi.Operator;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.logging.Level.SEVERE;

public abstract class SimpleController<T extends ObjectLike> {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final ExecutorService workers;
    private final ReentrantLock admission = new ReentrantLock();
    private final Condition available = admission.newCondition();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final JsonMapper jsonMapper;
    private final int threadCount;
    protected final Operator<T> operator;

    protected final OperatorState<T> state = new OperatorState<>();
    /**
     * Legacy subclass event ingress, including direct {@code offer}, {@code put} and {@code add} calls.
     * Prefer {@link #onEvent(String)} for admission synchronized with stop.
     * Stop snapshots pending entries; direct submissions after that snapshot are not processed.
     * This is a real, unbounded queue, not a view of the internal task queue.
     */
    protected final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final Queue<String> drainingEvents = new ArrayDeque<>();
    private boolean preferEvent = true;
    private boolean drained;
    protected volatile boolean stopping = false;

    /**
     * @param threads borrowed scheduler retained in the constructor for subclass compatibility;
     *                subclasses can use it for HTTP work and timers, but event loops run on isolated,
     *                owned workers so they cannot starve the scheduler. This controller never closes it.
     * @param jsonMapper event mapper.
     * @param threadCount number of owned event workers.
     * @param operator event callbacks.
     */
    public SimpleController(final ScheduledExecutorService threads,
                            final JsonMapper jsonMapper,
                            final int threadCount,
                            final Operator<T> operator) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("eventThreadCount must be positive");
        }
        this.workers = Executors.newFixedThreadPool(threadCount, task -> {
            final var thread = new Thread(task, "fusion-operator-events");
            thread.setDaemon(true);
            return thread;
        });
        this.jsonMapper = jsonMapper;
        this.threadCount = threadCount;
        this.operator = operator;
    }

    private void internalOnAdd(final T object) {
        stateAdd(object);
        operator.onAdd(object);
    }

    private void internalOnModified(final T object) {
        stateDelete(object);
        stateAdd(object);
        operator.onModify(object);
    }

    private void internalOnDelete(final T object) {
        stateDelete(object);
        operator.onDelete(object);
    }

    private void stateAdd(final T object) {
        stateDelete(object);
        state.items().add(object);
    }

    private void stateDelete(final T object) {
        state.items().removeIf(o -> Objects.equals(o.metadata().uid(), object.metadata().uid()));
    }

    public void init() {
        admission.lock();
        try {
            if (!stopping && initialized.compareAndSet(false, true)) {
                for (int i = 0; i < threadCount; i++) {
                    workers.execute(this::eventLoop);
                }
            }
        } finally {
            admission.unlock();
        }
    }

    public void onEvent(final String event) {
        admission.lock();
        try {
            if (!stopping) {
                queue.offer(event);
                available.signal();
            }
        } finally {
            admission.unlock();
        }
    }

    protected final void enqueue(final Runnable event) {
        admission.lock();
        try {
            if (!stopping) {
                tasks.offer(event);
                available.signal();
            }
        } finally {
            admission.unlock();
        }
    }

    // Only continuations of already admitted work may bypass shutdown admission.
    protected final void enqueueContinuation(final Runnable event) {
        admission.lock();
        try {
            if (!drained) {
                tasks.offer(event);
                available.signal();
            }
        } finally {
            admission.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    protected final void handleEvent(final String line) {
        final var data = jsonMapper.fromString(Event.class, line);
        if (data.type() == null || data.object() == null) {
            logger.warning("Ignoring event without type or object");
            return;
        }
        switch (data.type()) {
            case "ERROR" -> {
                logger.severe("Kubernetes ERROR event received");
                operator.onError(line);
            }
            case "BOOKMARK" -> {
                final var bookmark = data.object();
                if (bookmark != null) {
                    final var metadata = (Map<String, Object>) bookmark.get("metadata");
                    if (metadata != null) {
                        final var resourceVersion = (String) metadata.get("resourceVersion");
                        if (resourceVersion != null && !resourceVersion.isBlank()) {
                            operator.onBookmark(resourceVersion);
                            onBookmark(resourceVersion);
                        }
                    }
                }
            }
            case "ADDED" -> internalOnAdd(asObject(data.object()));
            case "MODIFIED" -> internalOnModified(asObject(data.object()));
            case "DELETED" -> internalOnDelete(asObject(data.object()));
            default -> logger.warning("Ignoring event with unsupported type");
        }
    }

    protected void onBookmark(final String resourceVersion) {
        // no-op
    }

    private T asObject(final Map<String, Object> object) {
        return jsonMapper.fromString(operator.resourceType(), jsonMapper.toString(object));
    }

    private void eventLoop() {
        while (true) {
            try {
                final var next = nextTask();
                if (next == null) {
                    break;
                }
                next.run();
            } catch (final InterruptedException e) {
                logger.info("Exiting from controller on interruption");
                Thread.currentThread().interrupt();
                break;
            } catch (final RuntimeException e) {
                // Callback and parsing exceptions can contain credentials, URLs or resource bodies.
                logger.log(SEVERE, "Event handler failed; continuing with the next event");
            }
        }
        logger.info("Exiting from controller");
    }

    // FIFO within each lane, alternating when both are busy. With multiple workers callback
    // completion order is intentionally unspecified, just as with a single shared work queue.
    private Runnable nextTask() throws InterruptedException {
        admission.lockInterruptibly();
        try {
            while (true) {
                final var events = stopping ? drainingEvents : queue;
                final var event = (preferEvent || tasks.isEmpty()) ? events.poll() : null;
                if (event != null) {
                    preferEvent = false;
                    return () -> handleEvent(event);
                }
                final var task = tasks.poll();
                if (task != null) {
                    preferEvent = true;
                    return task;
                }
                if (!events.isEmpty()) {
                    continue;
                }
                if (stopping) {
                    return null;
                }
                // Direct legacy queue writes cannot signal our condition.
                available.await(50, MILLISECONDS);
            }
        } finally {
            admission.unlock();
        }
    }

    public void stop() {
        admission.lock();
        try {
            if (!stopping) {
                stopping = true;
                // drainTo takes a finite snapshot even while legacy producers keep submitting.
                queue.drainTo(drainingEvents);
            }
            workers.shutdown();
            available.signalAll();
        } finally {
            admission.unlock();
        }
        final var deadline = System.nanoTime() + MILLISECONDS.toNanos(5_000);
        try {
            if (!workers.awaitTermination(Math.max(0, deadline - System.nanoTime()), NANOSECONDS)) {
                discardPending();
                workers.shutdownNow();
                logger.warning("Event drain exceeded 5 seconds; interrupting workers");
            }
        } catch (final InterruptedException e) {
            discardPending();
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            discardPending();
            queue.clear();
        }
    }

    private void discardPending() {
        admission.lock();
        try {
            drained = true;
            tasks.clear();
            drainingEvents.clear();
        } finally {
            admission.unlock();
        }
    }
}
