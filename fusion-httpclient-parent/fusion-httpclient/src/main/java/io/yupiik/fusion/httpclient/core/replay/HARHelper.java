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
package io.yupiik.fusion.httpclient.core.replay;

import io.yupiik.fusion.httpclient.core.listener.impl.BaseHARDumperListener;
import io.yupiik.fusion.httpclient.core.response.StaticHttpResponse;
import io.yupiik.fusion.httpclient.core.response.StaticResponseInfo;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

class HARHelper {
    public <T> StaticHttpResponse<T> toResponse(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler,
                                                final BaseHARDumperListener.Har.Entry inMemory) {
        final var info = new StaticResponseInfo(
                inMemory.response().status(), readHeaders(inMemory),
                httpVersion(inMemory.response().httpVersion()));
        return new StaticHttpResponse<>(
                request, info.version(), info.statusCode(), info.headers(),
                readBody(info, inMemory.response(), responseBodyHandler));
    }

    private HttpClient.Version httpVersion(final String version) {
        return version != null && version.startsWith("HTTP/2.") ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1;
    }

    private HttpHeaders readHeaders(final BaseHARDumperListener.Har.Entry inMemory) {
        return HttpHeaders.of(
                inMemory.response().headers() == null ?
                        Map.of() :
                        inMemory.response().headers().stream()
                                .collect(toMap(
                                        BaseHARDumperListener.Har.Header::name,
                                        h -> List.of(h.value()),
                                        (a, b) -> Stream.of(a, b).flatMap(Collection::stream).collect(toList()))),
                (a, b) -> true);
    }

    private <T> T readBody(final HttpResponse.ResponseInfo info, final BaseHARDumperListener.Har.Response response,
                           final HttpResponse.BodyHandler<T> handler) {
        if (response.content() != null) {
            if (response.content().text() != null) { // UTF-8 hardcoded in HARDumper for now
                return readBody(info, response.content().text().getBytes(UTF_8), handler);
            }
            if (response.content().encoding() != null) {
                return readBody(info, Base64.getDecoder().decode(response.content().encoding()), handler);
            }
        }
        return null;
    }

    private <T> T readBody(final HttpResponse.ResponseInfo info, final byte[] text,
                           final HttpResponse.BodyHandler<T> handler) {
        final var subscriber = handler.apply(info);
        try {
            // for now don't impl it since subscribers are generally simple enough (but TODO)
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(final long n) {
                    // no-op
                }

                @Override
                public void cancel() {
                    // no-op
                }
            });
            subscriber.onNext(List.of(ByteBuffer.wrap(text)));
            subscriber.onComplete();
        } catch (final RuntimeException re) { // very unlikely
            subscriber.onError(re);
        }
        try {
            return subscriber.getBody().toCompletableFuture().get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}
