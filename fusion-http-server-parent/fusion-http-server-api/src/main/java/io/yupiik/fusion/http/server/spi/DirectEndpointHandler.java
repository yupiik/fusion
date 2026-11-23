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
package io.yupiik.fusion.http.server.spi;

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.impl.io.RequestBodyAggregator;

import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Shared HTTP body pipeline with direct callbacks and no dispatcher on asynchronous continuations. */
public final class DirectEndpointHandler implements Function<Request, CompletionStage<Response>> {
    private final Type[] bodies;
    private final BiFunction<Request, Object[], CompletionStage<?>> invoker;
    private final BiFunction<Type, Object, Object> decoder;
    private final Function<Object, Response> serializer;

    public DirectEndpointHandler(final Type[] bodies,
                                 final BiFunction<Request, Object[], CompletionStage<?>> invoker,
                                 final BiFunction<Type, Object, Object> decoder,
                                 final Function<Object, Response> serializer) {
        if (bodies.length > 1) {
            throw new IllegalArgumentException("HTTP endpoints support at most one JSON body parameter.");
        }
        this.bodies = bodies.clone();
        this.invoker = invoker;
        this.decoder = decoder;
        this.serializer = serializer;
    }

    @Override
    public CompletionStage<Response> apply(final Request request) {
        return read(request, new Object[bodies.length], 0);
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Response> read(final Request request, final Object[] values, final int index) {
        if (index == bodies.length) {
            final var result = invoker.apply(request, values);
            return serializer == null ? (CompletionStage<Response>) result : result.thenApply(serializer);
        }
        final CompletionStage<?> body;
        final var stream = request.unwrapOrNull(InputStream.class);
        if (stream != null) {
            body = CompletableFuture.completedStage(decoder.apply(bodies[index], stream));
        } else {
            final var reader = request.unwrapOrNull(Reader.class);
            if (reader != null) {
                body = CompletableFuture.completedStage(decoder.apply(bodies[index], reader));
            } else {
                body = new RequestBodyAggregator(request.fullBody(), StandardCharsets.UTF_8).promise()
                        .thenApply(payload -> decoder.apply(bodies[index], payload));
            }
        }
        return body.thenCompose(value -> {
            values[index] = value;
            return read(request, values, index + 1);
        });
    }
}
