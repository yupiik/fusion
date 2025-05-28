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
package io.yupiik.fusion.http.server.impl.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

public class FusionWriteListener implements WriteListener {
    private static final Logger LOGGER = Logger.getLogger(FusionWriteListener.class.getName());

    private final Flow.Publisher<ByteBuffer> body;
    private final HttpServletResponse response;
    private final ServletOutputStream stream;
    private final CompletableFuture<Void> result;

    private Flow.Subscription subscription;
    private boolean closed = false;
    private final List<IORunnable> events = new ArrayList<>();
    private final Lock lock = new ReentrantLock();
    private final AtomicBoolean looping = new AtomicBoolean();
    private volatile boolean completed = false;

    public FusionWriteListener(final Flow.Publisher<ByteBuffer> body,
                               final HttpServletResponse response,
                               final ServletOutputStream stream,
                               final CompletableFuture<Void> result) {
        this.body = body;
        this.response = response;
        this.stream = stream;
        this.result = result;
        addEvent(new IORunnable("ctor::subscribe") {
            @Override
            public void run() {
                body.subscribe(new Subscriber());
            }
        });
    }

    @Override
    public void onWritePossible() {
        doLoop();
    }

    @Override
    public void onError(final Throwable throwable) {
        handleError(throwable);
    }

    private void doLoop() {
        if (!looping.compareAndSet(false, true)) {
            return;
        }
        try {
            loop();
        } finally {
            looping.set(false);
        }
    }

    private void loop() {
        try {
            while (!closed && stream.isReady()) {
                IORunnable removed;
                lock.lock();
                try {
                    if (events.isEmpty()) {
                        return;
                    }
                    removed = events.remove(0);
                } finally {
                    lock.unlock();
                }
                doRun(removed);
            }
        } catch (final IOException e) {
            handleError(e);
        }
    }

    protected void doRun(final IORunnable task) throws IOException {
        task.run();
    }

    private void addEvent(final IORunnable... task) {
        lock.lock();
        try {
            events.addAll(List.of(task));
        } finally {
            lock.unlock();
        }
        doLoop();
    }

    private void doClose() {
        try {
            if (closed) {
                return;
            }
            closed = true;
            stream.close();
            result.complete(null);
        } catch (final IOException e) {
            handleError(e);
        }
    }

    private void handleError(final Throwable throwable) {
        LOGGER.log(SEVERE, throwable, throwable::getMessage);
        try {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (!response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } finally {
                stream.close();
            }
        } catch (final IOException ioException) {
            LOGGER.log(SEVERE, ioException, ioException::getMessage);
        } finally {
            if (!result.isDone()) {
                result.completeExceptionally(throwable);
            }
        }
    }

    private class Subscriber implements Flow.Subscriber<ByteBuffer> {
        private final IORunnable requestNext = new IORunnable("onNext::request") {
            @Override
            public void run() {
                if (!completed) {
                    subscription.request(1);
                }
            }
        };
        private final IORunnable flush = new IORunnable("onNext::flush") {
            @Override
            public void run() throws IOException {
                stream.flush();
            }
        };

        @Override
        public void onSubscribe(final Flow.Subscription s) {
            subscription = s;
            addEvent(new IORunnable("onSubscribe::request") {
                @Override
                public void run() {
                    subscription.request(1);
                }
            });
        }

        @Override
        public void onNext(final ByteBuffer item) {
            addEvent(new IORunnable("onNext::write") {
                @Override
                public void run() throws IOException {
                    stream.write(item);
                }
            }, flush, requestNext);
        }

        @Override
        public void onComplete() {
            completed = true;
            addEvent(new IORunnable("onComplete::doClose") {
                @Override
                public void run() {
                    doClose();
                }
            });
        }

        @Override
        public void onError(final Throwable throwable) {
            handleError(throwable);
        }
    }

    protected static abstract class IORunnable {
        private final String toString;

        private IORunnable(final String toString) {
            this.toString = toString;
        }

        @Override
        public String toString() {
            return toString;
        }

        public abstract void run() throws IOException;
    }
}
