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
package io.yupiik.fusion.http.server.impl.servlet;

import io.yupiik.fusion.http.server.api.Body;
import io.yupiik.fusion.http.server.impl.flow.ServletInputStreamSubscription;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static java.net.http.HttpResponse.BodySubscribers.ofByteArray;
import static java.net.http.HttpResponse.BodySubscribers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

public class ServletBody implements Body {
    private final HttpServletRequest request;

    public ServletBody(final HttpServletRequest delegate) {
        this.request = delegate;
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
        try {
            subscriber.onSubscribe(new ServletInputStreamSubscription(request.getInputStream(), subscriber));
        } catch (final IOException e) {
            subscriber.onError(e);
        }
    }

    @Override
    public CompletionStage<String> string() {
        final var subscriber = ofString(ofNullable(request.getCharacterEncoding()).map(Charset::forName).orElse(UTF_8));
        doSubscribe(subscriber);
        return subscriber.getBody();
    }

    @Override
    public CompletionStage<byte[]> bytes() {
        final var subscriber = ofByteArray();
        doSubscribe(subscriber);
        return subscriber.getBody();
    }

    private <T> void doSubscribe(final HttpResponse.BodySubscriber<T> subscriber) {
        subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(final ByteBuffer item) {
                subscriber.onNext(List.of(item));
            }

            @Override
            public void onError(final Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }
}
