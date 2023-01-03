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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;

/**
 * Creates a collector of span which triggers a flush when the buffer reaches its max size.
 * You can combine it with a scheduled flushing if you need to but the scheduler handling is out of scope of this class.
 * <p>
 * You will generally set a {@code onFlush} callback to actually push somewhere the spans - by default nothing is done.
 */
public class AccumulatingSpanCollector implements Consumer<Span>, AutoCloseable {
    private final Map<Span, Span> buffer = new ConcurrentHashMap<>();
    private final int bufferSize;
    private Consumer<Collection<Span>> onFlush;
    private volatile boolean closed = true;

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

        buffer.put(span, span);

        // prefer to flush after to ensure we flush on event to not have a pattern encouraging to have staled entries
        // note: it can lead to not strictly respecting the buffer size, it is fine
        if (buffer.size() > bufferSize) {
            synchronized (this) {
                if (buffer.size() > bufferSize) {
                    flush();
                }
            }
        }
    }

    public void flush() {
        if (onFlush == null) {
            return;
        }

        final var spans = buffer.keySet().stream().limit(bufferSize).collect(toList());
        onFlush.accept(spans);
        spans.forEach(buffer::remove);
    }

    @Override
    public void close() {
        closed = true;
        flush();
    }
}
