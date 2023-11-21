/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.tracing.collector;

import io.yupiik.fusion.tracing.span.Span;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Creates a collector of span which triggers a flush when the buffer reaches its max size.
 * You can combine it with a scheduled flushing if you need to but the scheduler handling is out of scope of this class.
 * <p>
 * You will generally set a {@code onFlush} callback to actually push somewhere the spans - by default nothing is done.
 */
public class AccumulatingSpanCollector implements Consumer<Span>, AutoCloseable {
    private final Buffer<Span> buffer = new Buffer<>();
    private final int bufferSize;
    private Consumer<Collection<Span>> onFlush;
    private volatile boolean closed = true;
    private final ReentrantLock lock = new ReentrantLock();

    public AccumulatingSpanCollector() {
        this(4096);
    }

    /**
     * @param bufferSize max size before forcing a flush of spans.
     */
    public AccumulatingSpanCollector(final int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public AccumulatingSpanCollector setOnFlush(final Consumer<Collection<Span>> onFlush) {
        this.onFlush = onFlush;
        this.closed = false;
        return this;
    }

    @Override
    public void accept(final Span span) {
        if (closed || onFlush == null) {
            return;
        }

        if (bufferSize <= 0) {
            onFlush.accept(List.of(span));
            return;
        }

        buffer.add(span);

        // prefer to flush after to ensure we flush on event to not have a pattern encouraging to have staled entries
        // note: it can lead to not strictly respecting the buffer size, it is fine
        if (buffer.size() > bufferSize) {
            Collection<Span> spans = List.of();
            lock.lock();
            try {
                if (buffer.size() > bufferSize) {
                    spans = buffer.drain();
                }
            } finally {
                lock.unlock();
            }
            if (!spans.isEmpty()) {
                onFlush.accept(spans);
            }
        }
    }

    public void flush() {
        if (onFlush != null) {
            lock.lock();
            try {
                onFlush.accept(buffer.drain());
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        if (onFlush != null) {
            lock.lock();
            try {
                while (buffer.size() > 0) {
                    onFlush.accept(buffer.drain());
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
