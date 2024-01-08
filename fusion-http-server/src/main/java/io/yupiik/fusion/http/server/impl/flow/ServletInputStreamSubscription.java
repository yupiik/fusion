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

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServletInputStreamSubscription implements Flow.Subscription, ReadListener {
    private static final Logger LOGGER = Logger.getLogger(ServletInputStreamSubscription.class.getName());

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024; // org.apache.catalina.connector.InputBuffer.DEFAULT_BUFFER_SIZE

    private final ServletInputStream inputStream;
    private final ReadableByteChannel channel;
    private final Flow.Subscriber<? super ByteBuffer> downstream;

    private volatile boolean cancelled = false;
    private volatile long requested = 0;

    public ServletInputStreamSubscription(final ServletInputStream inputStream, final Flow.Subscriber<? super ByteBuffer> downstream) {
        this.inputStream = inputStream;
        this.channel = Channels.newChannel(inputStream);
        this.downstream = downstream;
        inputStream.setReadListener(this);
    }

    private void readIfPossible() {
        if (cancelled || requested == 0) {
            return;
        }

        try {
            long loop = requested;
            while (loop > 0) {
                if (!inputStream.isReady()) {
                    break;
                }

                final var buffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE); // todo: reuse buffers to limit allocations
                final var read = channel.read(buffer);
                if (read <= 0) {
                    break;
                }

                buffer.position(0).limit(read);
                downstream.onNext(buffer);
                loop--;
            }
        } catch (final IOException | RuntimeException re) {
            onError(re);
        }
    }

    @Override
    public synchronized void onDataAvailable() {
        readIfPossible();
    }

    @Override
    public synchronized void onAllDataRead() {
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
    public synchronized void request(final long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Invalid request: " + n + ", should be > 0");
        }
        if (cancelled) {
            return;
        }
        requested += n;
        readIfPossible();
    }

    @Override
    public synchronized void onError(final Throwable throwable) {
        LOGGER.log(Level.SEVERE, throwable, throwable::getMessage);
        if (cancelled) {
            return;
        }
        doClose();
        downstream.onError(throwable);
        cancelled = true;
    }

    private void doClose() {
        if (channel.isOpen()) {
            try {
                channel.close();
            } catch (final IOException e) {
                LOGGER.log(Level.SEVERE, e, e::getMessage);
            }
        }
    }

    @Override
    public synchronized void cancel() {
        cancelled = true;
    }
}
