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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class SimpleController<T extends ObjectLike> {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final ScheduledExecutorService threads;
    private final JsonMapper jsonMapper;
    private final int threadCount;
    private final Operator<T> operator;

    protected final OperatorState<T> state = new OperatorState<>();
    protected final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    protected volatile boolean stopping = false;

    public SimpleController(final ScheduledExecutorService threads,
                            final JsonMapper jsonMapper,
                            final int threadCount,
                            final Operator<T> operator) {
        this.threads = threads;
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
        state.items().add(object);
    }

    private void stateDelete(final T object) {
        state.items().removeIf(o -> Objects.equals(o.metadata().uid(), object.metadata().uid()));
    }

    public void init() {
        for (int i = 0; i < threadCount; i++) {
            this.threads.execute(this::eventLoop);
        }
    }

    public void onEvent(final String event) {
        if (!queue.offer(event)) {
            logger.severe(() -> "Can't handle event '" + event + "', ignoring");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleEvent(final String line) {
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
                final var bookmark = (Map<String, Object>) jsonMapper.fromString(Object.class, line);
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
        while (!stopping) {
            try {
                final var poll = queue.poll(10, SECONDS);
                if (poll != null) {
                    handleEvent(poll);
                }
            } catch (final InterruptedException e) {
                logger.info("Exiting from controller on interruption");
                Thread.currentThread().interrupt();
                break;
            }
        }
        logger.info("Exiting from controller");
    }

    public void stop() {
        stopping = true;
    }
}
