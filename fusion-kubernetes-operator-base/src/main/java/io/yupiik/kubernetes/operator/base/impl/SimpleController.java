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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.logging.Level.SEVERE;

public abstract class SimpleController<T extends ObjectLike> {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final ExecutorService workers;
    private final ReentrantLock admission = new ReentrantLock();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final JsonMapper jsonMapper;
    private final int threadCount;
    protected final Operator<T> operator;

    protected final OperatorState<T> state = new OperatorState<>();
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    protected volatile boolean stopping = false;

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
        enqueue(() -> handleEvent(event));
    }

    protected final void enqueue(final Runnable event) {
        admission.lock();
        try {
            if (!stopping) {
                queue.offer(event);
            }
        } finally {
            admission.unlock();
        }
    }

    // Only continuations of already admitted work may bypass shutdown admission.
    protected final void enqueueContinuation(final Runnable event) {
        queue.offer(event);
    }

    @SuppressWarnings("unchecked")
    protected final void handleEvent(final String line) {
        final var data = jsonMapper.fromString(Event.class, line);
        if (data.type() == null || data.object() == null) {
            logger.warning(() -> "Ignoring event: " + data);
            return;
        }
        switch (data.type()) {
            case "ERROR" -> {
                logger.severe(line);
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
            default -> logger.warning(() -> "Ignoring event: " + data);
        }
    }

    protected void onBookmark(final String resourceVersion) {
        // no-op
    }

    private T asObject(final Map<String, Object> object) {
        return jsonMapper.fromString(operator.resourceType(), jsonMapper.toString(object));
    }

    private void eventLoop() {
        while (!stopping || !queue.isEmpty()) {
            try {
                final var poll = queue.poll(50, MILLISECONDS);
                if (poll != null) {
                    poll.run();
                }
            } catch (final InterruptedException e) {
                logger.info("Exiting from controller on interruption");
                Thread.currentThread().interrupt();
                break;
            } catch (final RuntimeException e) {
                logger.log(SEVERE, "Event handler failed; continuing with the next event", e);
            }
        }
        logger.info("Exiting from controller");
    }

    public void stop() {
        admission.lock();
        try {
            stopping = true;
            workers.shutdown();
        } finally {
            admission.unlock();
        }
        final var deadline = System.nanoTime() + MILLISECONDS.toNanos(5_000);
        try {
            if (!workers.awaitTermination(Math.max(0, deadline - System.nanoTime()), NANOSECONDS)) {
                queue.clear();
                workers.shutdownNow();
                logger.warning("Event drain exceeded 5 seconds; interrupting workers");
            }
        } catch (final InterruptedException e) {
            queue.clear();
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
