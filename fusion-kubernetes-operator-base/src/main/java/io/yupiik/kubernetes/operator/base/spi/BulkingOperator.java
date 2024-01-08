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
package io.yupiik.kubernetes.operator.base.spi;

import io.yupiik.kubernetes.operator.base.impl.ObjectLike;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.SEVERE;

public abstract class BulkingOperator<T extends ObjectLike> extends Operator.Base<T> {
    private final int bulkSize;
    private final long timeout;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<Event<T>> buffer;
    private ScheduledFuture<?> flushingTask;
    private Instant nextFlush;

    public BulkingOperator(final Class<T> resourceType, final DefaultOperatorConfiguration configuration,
                           final int bulkSize, final long timeout, final ScheduledExecutorService scheduler,
                           final Clock clock) {
        super(resourceType, configuration);
        this.bulkSize = bulkSize;
        this.timeout = timeout;
        this.scheduler = scheduler;
        this.clock = clock;
        this.buffer = new ArrayList<>(bulkSize);
    }

    protected void onBulk(final List<Event<T>> events) {
        // no-op
    }

    @Override
    public CompletionStage<?> onStart() {
        scheduleFlush(clock.instant());
        return super.onStart();
    }

    @Override
    public void onStop() {
        lock.lock();
        try {
            if (flushingTask != null) {
                flushingTask.cancel(true);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onAdd(final T resource) {
        onEvent(new Event<>(Type.ADDED, resource));
    }

    @Override
    public void onModify(final T resource) {
        onEvent(new Event<>(Type.MODIFIED, resource));
    }

    @Override
    public void onDelete(final T resource) {
        onEvent(new Event<>(Type.DELETED, resource));
    }

    private void onEvent(final Event<T> event) {
        lock.lock();
        try {
            buffer.add(event);
        } finally {
            lock.unlock();
        }


        if (!flush()) {
            final var now = clock.instant();
            if (nextFlush == null || nextFlush.isBefore(now)) {
                scheduleFlush(now);
            }
        }
    }

    private void scheduleFlush(final Instant now) {
        lock.lock();
        try {
            if (nextFlush != null && nextFlush.isAfter(now)) {
                return; // already done
            }

            if (flushingTask != null) {
                flushingTask.cancel(false);
            }

            flushingTask = scheduler.schedule(this::flush, timeout, MILLISECONDS);
            nextFlush = clock.instant().plusMillis(timeout);
        } finally {
            lock.unlock();
        }
    }

    private boolean flush() {
        List<Event<T>> bulkable = null;
        lock.lock();
        try {
            if (buffer.size() >= bulkSize) {
                bulkable = new ArrayList<>(buffer);
                buffer.clear();
            }
        } finally {
            lock.unlock();
        }

        if (bulkable == null || bulkable.isEmpty() /* unlikely bulksize=0 */) {
            return false;
        }

        try {
            onBulk(bulkable);
        } catch (final RuntimeException re) {
            Logger.getLogger(getClass().getName()).log(SEVERE, re, re::getMessage);
        } finally {
            lock.lock();
            try {
                if (flushingTask != null) {
                    flushingTask.cancel(false);
                    flushingTask = null;
                }
            } finally {
                lock.unlock();
            }
        }
        return true;
    }

    protected record Event<T>(Type type, T resource) {
    }

    protected enum Type {
        ADDED, DELETED, MODIFIED
    }
}
