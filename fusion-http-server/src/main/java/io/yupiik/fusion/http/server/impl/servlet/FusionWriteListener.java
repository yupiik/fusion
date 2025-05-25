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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;

public class FusionWriteListener implements WriteListener {
    private static final Logger LOGGER = Logger.getLogger(FusionWriteListener.class.getName());

    private final Flow.Publisher<ByteBuffer> body;
    private final HttpServletResponse response;
    private final ServletOutputStream stream;
    private final CompletableFuture<Void> result;

    private Flow.Subscription subscription;
    private ByteBuffer pendingBuffer;
    private Action action = Action.SUBSCRIBE;
    private boolean closed = false;
    private boolean completed = false;

    public FusionWriteListener(final Flow.Publisher<ByteBuffer> body,
                               final HttpServletResponse response,
                               final ServletOutputStream stream,
                               final CompletableFuture<Void> result) {
        this.body = body;
        this.response = response;
        this.stream = stream;
        this.result = result;
    }

    @Override
    public void onWritePossible() {
        loop();
    }

    @Override
    public void onError(final Throwable throwable) {
        handleError(throwable);
    }

    // normally we do not need to lock since we make everything sequential even if on multiple threads
    private void loop() {
        try {
            while (!closed && stream.isReady()) {
                switch (action) {
                    case AWAITING -> {
                        return;
                    }
                    case REQUEST -> {
                        if (subscription == null || result.isDone()) {
                            continue;
                        }
                        if (!completed) {
                            action = Action.AWAITING;
                            subscription.request(1);
                        } else {
                            doClose();
                        }
                        return;
                    }
                    case WRITE -> {
                        final var ref = pendingBuffer;
                        pendingBuffer = null;
                        doWrite(ref);
                    }
                    case FLUSH -> {
                        stream.flush();
                        action = Action.REQUEST;
                    }
                    case SUBSCRIBE -> {
                        if (subscription == null) {
                            body.subscribe(new Subscriber());
                            action = Action.REQUEST;
                        }
                    }
                }
            }
        } catch (final IOException e) {
            handleError(e);
        }
    }

    private void doWrite(final ByteBuffer pendingBuffer) throws IOException {
        stream.write(pendingBuffer);
        action = Action.FLUSH;
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
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            if (!closed) {
                closed = true;
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
        @Override
        public void onSubscribe(final Flow.Subscription s) {
            subscription = s;
        }

        @Override
        public void onNext(final ByteBuffer item) {
            pendingBuffer = item;
            action = Action.WRITE;
            triggerEventLoop();
        }

        @Override
        public void onError(final Throwable throwable) {
            handleError(throwable);
        }

        @Override
        public void onComplete() {
            completed = true;
            if (action == Action.AWAITING) {
                action = Action.REQUEST;
            }
            if (!closed) {
                triggerEventLoop();
            }
        }

        private void triggerEventLoop() {
            loop();
        }
    }

    // defines the state machine we want, ie this flow:
    // 1. request(1)
    // 2. write(buffer)
    // 3. flush()
    private enum Action {
        SUBSCRIBE, REQUEST, WRITE, FLUSH, AWAITING
    }
}
