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
package io.yupiik.fusion.jsonrpc;

import io.yupiik.fusion.http.server.api.IOConsumer;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.impl.DefaultEndpoint;
import io.yupiik.fusion.json.JsonMapper;

import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import static io.yupiik.fusion.jsonrpc.JsonRpcHandler.RESPONSE_HEADERS;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.SEVERE;

public class JsonRpcEndpoint extends DefaultEndpoint {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final JsonRpcHandler handler;
    private final JsonMapper mapper;
    private final boolean useInputStream;
    private final int voidStatus;
    private final String contentType;

    public JsonRpcEndpoint(final JsonRpcHandler handler, final JsonMapper mapper, final String path, final boolean useInputStream) {
        this(handler, mapper, path, useInputStream, 202, "application/json;charset=utf-8");
    }

    public JsonRpcEndpoint(final JsonRpcHandler handler, final JsonMapper mapper, final String path,
                           final boolean useInputStream, final int voidStatus, final String contentType) {
        super(
                1000,
                r -> "POST".equals(r.method()) && path.equals(r.path()),
                null);
        this.useInputStream = useInputStream;
        this.handler = handler;
        this.mapper = mapper;
        this.voidStatus = voidStatus;
        this.contentType = contentType;
    }

    @Override
    public CompletionStage<Response> handle(final Request request) {
        final CompletionStage<Object> req;
        try { // deserialization error
            req = readRequest(request);
        } catch (final RuntimeException ex) {
            return completedFuture(jsonRpcError(-32700, ex, request));
        }
        // todo: add Before event using the bus to enable security validation -
        //  can be done wrapping the endpoint + overriding (priority) it in the IoC as of today?
        return req
                .thenCompose(in -> handler
                        .execute(in, request)
                        .thenApply(it -> response(it, request))
                        .exceptionally(ex -> {
                            logger.log(SEVERE, ex, ex::getMessage);
                            return jsonRpcError(-32603, ex, request);
                        }))
                .exceptionally(error -> jsonRpcError(-32700, error, request));
    }

    private CompletionStage<Object> readRequest(final Request request) {
        try {
            if (!useInputStream) {
                final var reader = request.unwrapOrNull(Reader.class);
                if (reader != null) {
                    return completedFuture(mapper.read(Object.class, reader));
                }
            }
            return handler.readRequest(request.body());
        } catch (final IllegalArgumentException iae) {
            logger.log(FINEST, iae, () -> "canUnwrapAsReader=true but reader was not extracted from the request: " + iae.getMessage());
            return handler.readRequest(request.body());
        }
    }

    private Response jsonRpcError(final int code, final Throwable error, final Request request) {
        return response(handler.createResponse(null, code, error.getMessage()), request);
    }

    @SuppressWarnings("unchecked")
    private Response response(final Object payload, final Request request) {
        final var attribute = request.attribute(RESPONSE_HEADERS, Map.class);
        final var res = Response.of();
        res.header("content-type", contentType);
        if (attribute != null) {
            ((Map<String, String>) attribute).forEach(res::header);
        }
        if (payload == null) {
            res.status(voidStatus);
        } else {
            res
                    .status(200)
                    .body((IOConsumer<Writer>) writer -> {
                        try (writer) {
                            mapper.write(payload, writer);
                        }
                    });
        }
        return res.build();
    }
}
