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

import io.yupiik.fusion.http.server.api.IOConsumer;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.SEVERE;

public class WriterPublisher implements Flow.Publisher<ByteBuffer> {
    private final IOConsumer<Writer> delegate;

    public WriterPublisher(final IOConsumer<Writer> delegate) {
        this.delegate = delegate;
    }

    // optimization path
    public IOConsumer<Writer> getDelegate() {
        return delegate;
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) { // shouldn't be used but impl for compat
        subscriber.onSubscribe(new Flow.Subscription() {
            private boolean cancelled;
            private Writer writer;
            private final AtomicLong requested = new AtomicLong();
            private final LinkedList<ByteBuffer> available = new LinkedList<>();
            private final ReentrantLock simpleLock = new ReentrantLock();

            @Override
            public void request(final long n) {
                if (n <= 0) {
                    throw new IllegalArgumentException("Invalid request count: " + n + ", should be > 0");
                }
                if (cancelled) {
                    return;
                }

                requested.addAndGet(n);

                simpleLock.lock();
                try {
                    serveRequested();

                    if (requested.get() == 0) {
                        return;
                    }

                    if (writer == null) {
                        writer = new Writer() {
                            private final Charset converter = UTF_8;

                            @Override
                            public void write(final char[] cbuf, final int off, final int len) {
                                if (len == 0) {
                                    return;
                                }

                                simpleLock.lock();
                                try {
                                    available.add(converter.encode(CharBuffer.wrap(cbuf, off, len)));
                                    serveRequested();
                                } catch (final RuntimeException re) {
                                    log(re);
                                    subscriber.onError(re);
                                    doClose();
                                } finally {
                                    simpleLock.unlock();
                                }
                            }

                            @Override
                            public void flush() {
                                // no-op
                            }

                            @Override
                            public void close() {
                                simpleLock.lock();
                                try {
                                    if (cancelled) {
                                        return;
                                    }
                                    try {
                                        serveRequested();
                                        subscriber.onComplete();
                                    } catch (final RuntimeException re) {
                                        log(re);
                                        subscriber.onError(re);
                                    }
                                    doClose();
                                } finally {
                                    simpleLock.unlock();
                                }
                            }
                        };
                        try {
                            delegate.accept(writer);
                        } catch (final IOException | RuntimeException e) {
                            log(e);
                            subscriber.onError(e);
                            doClose();
                        }
                    }
                } finally {
                    simpleLock.unlock();
                }
            }

            private void serveRequested() {
                if (!available.isEmpty() && requested.get() > 0) {
                    try {
                        subscriber.onNext(available.pollFirst());
                        requested.decrementAndGet();
                    } catch (final RuntimeException re) {
                        log(re);
                        subscriber.onError(re);
                        cancelled = true;
                        doClose();
                    }
                }
            }

            @Override
            public void cancel() {
                simpleLock.lock();
                try {
                    doClose();
                } finally {
                    simpleLock.unlock();
                }
            }

            private void doClose() {
                cancelled = true;
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (final IOException e) {
                        log(e);
                    } finally {
                        writer = null;
                    }
                }
            }
        });
    }

    private void log(final Exception re) {
        Logger.getLogger(getClass().getName()).log(SEVERE, re, re::getMessage);
    }
}
