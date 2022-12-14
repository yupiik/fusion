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

            @Override
            public synchronized void request(final long n) {
                if (n <= 0) {
                    throw new IllegalArgumentException("Invalid request count: " + n + ", should be > 0");
                }
                if (cancelled) {
                    return;
                }

                requested.addAndGet(n);

                serveRequested();

                if (requested.get() == 0) {
                    return;
                }

                if (writer == null) {
                    final var self = this;
                    writer = new Writer() {
                        private final Charset converter = UTF_8;

                        @Override
                        public void write(final char[] cbuf, final int off, final int len) {
                            if (len == 0) {
                                return;
                            }

                            try {
                                synchronized (self) {
                                    available.add(converter.encode(CharBuffer.wrap(cbuf, off, len)));
                                    serveRequested();
                                }
                            } catch (final RuntimeException re) {
                                log(re);
                                subscriber.onError(re);
                                synchronized (self) {
                                    doClose();
                                }
                            }
                        }

                        @Override
                        public void flush() {
                            // no-op
                        }

                        @Override
                        public void close() {
                            synchronized (self) {
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
            public synchronized void cancel() {
                doClose();
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
