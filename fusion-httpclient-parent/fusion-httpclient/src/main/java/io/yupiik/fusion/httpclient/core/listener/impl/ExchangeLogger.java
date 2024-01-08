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
package io.yupiik.fusion.httpclient.core.listener.impl;

import io.yupiik.fusion.httpclient.core.listener.RequestListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Usage:
 * <p>
 * {@code new ExchangeLogger(
 * Logger.getLogger(getClass().getName()),
 * Clock.systemUTC(),
 * clientConfiguration.isLogPayloads())}
 */
public class ExchangeLogger implements RequestListener<ExchangeLogger.Data> {
    private final Logger logger;
    private final Clock clock;
    private final boolean logPayload;

    public ExchangeLogger(final Logger logger, final Clock clock, final boolean logPayload) {
        this.logger = logger;
        this.clock = clock;
        this.logPayload = logPayload;
    }

    @Override
    public State<Data> before(final long count, final HttpRequest request) {
        return new State<>(request, new Data(count, clock.instant()));
    }

    @Override
    public void after(final Data before, final HttpRequest request, final Throwable error, final HttpResponse<?> response) {
        logger.info(() -> toLogMessage(before, request, error, response));
    }

    protected String toLogMessage(final Data before, final HttpRequest request, final Throwable error, final HttpResponse<?> response) {
        return "#" + before.count + ", " +
                "Request: '" + request.method() + " " + request.uri() + "' took " +
                clock.instant().minusMillis(before.instant.toEpochMilli()).toEpochMilli() + "ms\n" +
                (logPayload ? request.bodyPublisher()
                        .map(it -> {
                            final var out = new ByteArrayOutputStream();
                            it.subscribe(new Flow.Subscriber<>() {
                                @Override
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    subscription.request(Long.MAX_VALUE);
                                }

                                @Override
                                public void onNext(final ByteBuffer item) {
                                    int l = item.remaining();
                                    final var buffer = new byte[l];
                                    item.get(buffer, 0, l);
                                    try {
                                        out.write(buffer);
                                    } catch (final IOException e) {
                                        // no-op
                                    }
                                }

                                @Override
                                public void onError(final Throwable throwable) {
                                    try {
                                        out.write("<can't extract payload>".getBytes(UTF_8));
                                    } catch (final IOException e) {
                                        // no-op
                                    }
                                }

                                @Override
                                public void onComplete() {
                                    // no-op
                                }
                            });
                            return "\nPayload:\n" + out.toString(UTF_8);
                        })
                        .orElse("") : "") +
                "Response: " + (error != null ? "[ERROR] " + error.getMessage() : ("HTTP " + response.statusCode())) +
                (logPayload ? "\nPayload:\n" + (response == null ? "-" : response.body()) : "");
    }

    protected static class Data {
        private final long count;
        private final Instant instant;

        protected Data(final long count, final Instant instant) {
            this.count = count;
            this.instant = instant;
        }
    }
}
