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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Creates a collector of span which triggers a flush when the buffer reaches its max size.
 * You can combine it with a scheduled flushing if you need to but the scheduler handling is out of scope of this class.
 * <p>
 * You will generally set a {@code onFlush} callback to actually push somewhere the spans - by default nothing is done.
 */
public class AccumulatingSpanCollector implements Consumer<Span>, AutoCloseable {
    private final Buffer<Span> buffer = new Buffer<>();
    private final int bufferSize;
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> flushTask;
    private final boolean async;
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
        this(new Configuration().setBufferSize(bufferSize));
    }

    /**
     * @param configuration collector configuration.
     */
    public AccumulatingSpanCollector(final Configuration configuration) {
        this.bufferSize = configuration.bufferSize;
        this.async = configuration.asyncThreads > 0;
        if (configuration.flushInterval > 0 || configuration.asyncThreads > 0) {
            scheduler = Executors.newScheduledThreadPool(
                    // we need 1 for flushing and rest is for async sending to zipkin
                    1 + Math.max(configuration.asyncThreads, 0),
                    new ThreadFactory() {
                        private final AtomicInteger counter = new AtomicInteger();

                        @Override
                        public Thread newThread(final Runnable r) {
                            final var thread = new Thread(r, AccumulatingSpanCollector.class.getName() + "-" + counter.incrementAndGet());
                            thread.setContextClassLoader(AccumulatingSpanCollector.class.getClassLoader());
                            return thread;
                        }
                    });
            if (configuration.flushInterval > 0) {
                flushTask = scheduler.scheduleAtFixedRate(this::flush, configuration.flushInterval, configuration.flushInterval, MILLISECONDS);
            } else {
                flushTask = null;
            }
        } else {
            scheduler = null;
            flushTask = null;
        }
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
            doFlush(List.of(span));
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
                doFlush(spans);
            }
        }
    }

    private void doFlush(final Collection<Span> spans) {
        if (async && !scheduler.isShutdown()) {
            scheduler.execute(() -> onFlush.accept(spans));
        } else {
            onFlush.accept(spans);
        }
    }

    public void flush() {
        if (onFlush != null) {
            final Collection<Span> spans;
            lock.lock();
            try {
                spans = buffer.drain();
            } finally {
                lock.unlock();
            }
            if (!spans.isEmpty()) {
                doFlush(spans);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        if (flushTask != null) {
            flushTask.cancel(false);
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(1, MINUTES)) {
                    Logger.getLogger(AccumulatingSpanCollector.class.getName()).warning(() -> "Can't flush spans in 1mn, giving up");
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (onFlush != null) {
            lock.lock();
            try {
                while (buffer.size() > 0) {
                    doFlush(buffer.drain());
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public static class Configuration {
        /**
         * How many threads can send spans, if negative it will use the caller thread to call the {@code onFlush} callback.
         * This is mainly for synchronous flushers case.
         */
        private int asyncThreads = 8;

        /**
         * How many spans can be kept in memory max.
         */
        private int bufferSize = 4096;

        /**
         * How long to force a flush of the buffer if no new span are appended (max duration without zipking seeing the spans for ex).
         */
        private long flushInterval = 30_000L;

        public Configuration setAsyncThreads(final int asyncThreads) {
            this.asyncThreads = asyncThreads;
            return this;
        }

        public Configuration setBufferSize(final int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Configuration setFlushInterval(final long flushInterval) {
            this.flushInterval = flushInterval;
            return this;
        }
    }

    public static class Noop extends AccumulatingSpanCollector {
        public Noop() {
            super(new Configuration().setBufferSize(-1).setAsyncThreads(-1).setFlushInterval(-1));
        }

        @Override
        public void accept(final Span span) {
            // no-op
        }
    }
}
