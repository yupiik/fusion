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
package io.yupiik.fusion.http.server.impl.flow;

import io.yupiik.fusion.http.server.impl.servlet.ByteBufferPool;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServletInputStreamSubscription implements Flow.Subscription, ReadListener {
    private static final Logger LOGGER = Logger.getLogger(ServletInputStreamSubscription.class.getName());

    private final ServletInputStream inputStream;
    private final Flow.Subscriber<? super ByteBuffer> downstream;
    private final ByteBufferPool pool;

    private volatile boolean cancelled = false;
    private volatile boolean ready = false;
    private final AtomicLong requested = new AtomicLong();
    private final AtomicBoolean reading = new AtomicBoolean();

    public ServletInputStreamSubscription(
            final ByteBufferPool pool,
            final ServletInputStream inputStream,
            final Flow.Subscriber<? super ByteBuffer> downstream) {
        this.pool = pool;
        this.inputStream = inputStream;
        this.downstream = downstream;
        inputStream.setReadListener(this);
    }

    private void readIfPossible() {
        if (cancelled) {
            return;
        }

        try {
            if (inputStream.isFinished()) {
                return;
            }

            if (!reading.compareAndSet(false, true)) {
                return;
            }
            while (true) {
                if (!inputStream.isReady()) {
                    break;
                }

                if (requested.getAndUpdate(i -> i > 0 ? i - 1 : 0) == 0) {
                    break;
                }

                final var buffer = pool.get();
                final var read = inputStream.read(buffer);
                if (read <= 0) {
                    break;
                }

                if (!pool.needsRelease()) {
                    downstream.onNext(buffer);
                } else {
                    try { // copy cause we return early the buffer, fixme: enhance it by using reference counting instead of this simple pattern?
                        downstream.onNext(ByteBuffer.allocate(buffer.remaining()).put(buffer).flip());
                    } finally {
                        pool.release(buffer); // note that we assume release calls flip()
                    }
                }
            }
        } catch (final IOException | RuntimeException re) {
            onError(re);
        } finally {
            reading.set(false);
        }
    }

    @Override
    public void onDataAvailable() {
        ready = true;
        readIfPossible();
    }

    @Override
    public void onAllDataRead() {
        ready = true;
        if (cancelled) {
            return;
        }
        readIfPossible();
        if (!cancelled) {
            downstream.onComplete();
            doClose();
        }
    }

    @Override
    public void request(final long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Invalid request: " + n + ", should be > 0");
        }
        if (cancelled) {
            return;
        }
        if (n == Long.MAX_VALUE) { // no backpressure caller
            requested.set(Long.MAX_VALUE);
        } else {
            requested.updateAndGet(i -> {
                if (i == Long.MAX_VALUE) {
                    return i;
                }
                try {
                    return Math.addExact(i, n);
                } catch (final ArithmeticException e) {
                    return Long.MAX_VALUE;
                }
            });
        }
        if (ready) {
            readIfPossible();
        }
    }

    @Override
    public void onError(final Throwable throwable) {
        LOGGER.log(Level.SEVERE, throwable, throwable::getMessage);
        if (cancelled) {
            return;
        }

        cancelled = true;
        doClose();
        downstream.onError(throwable);
    }

    @Override
    public void cancel() {
        cancelled = true;
        doClose();
    }

    private void doClose() {
        try {
            inputStream.close();
        } catch (final IOException e) {
            LOGGER.log(Level.SEVERE, e, e::getMessage);
        }
    }
}
