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

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.impl.io.RequestBodyAggregator;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.deserialization.AvailableCharArrayReader;
import io.yupiik.fusion.jsonrpc.api.PartialResponse;
import io.yupiik.fusion.jsonrpc.event.BeforeRequest;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class JsonRpcHandler {
    private static final String REQUEST_METHOD_ATTRIBUTE = "yupiik.jsonrpc.method";
    static final String RESPONSE_HEADERS = "yupiik.jsonrpc.response.headers";

    private final CompletionStage<?> alwaysReady = completedFuture(null);
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final Emitter emitter;
    private final JsonMapper mapper;
    private final JsonRpcRegistry registry;

    public JsonRpcHandler(final RuntimeContainer emitter, final JsonMapper mapper, final JsonRpcRegistry registry) {
        this.emitter = emitter.getListeners().hasDirectListener(BeforeRequest.class) ? emitter : null;
        this.mapper = mapper;
        this.registry = registry;
    }

    public CompletionStage<Object> readRequest(final Flow.Publisher<ByteBuffer> payload) {
        return new RequestBodyAggregator(payload, UTF_8)
                .promise()
                .thenApply(chars -> mapper.read(Object.class, new AvailableCharArrayReader(chars)));
    }

    public CompletionStage<Response> handleRequest(final Map<String, Object> request, final Request httpRequest) {
        return doValidate(request)
                .map(CompletableFuture::completedFuture)
                .orElseGet(() -> doHandle(request, httpRequest));
    }

    public CompletableFuture<Response> doHandle(final Map<String, Object> request, final Request httpRequest) {
        final var method = request.get("method").toString();
        final var fn = registry.methods().get(method);
        final var id = request.get("id");
        final var params = request.get("params");

        if (httpRequest != null) {
            appendJsonRpcMethod(httpRequest, method);
        }

        try {
            return fn
                    .invoke(new JsonRpcMethod.Context(httpRequest, params))
                    .handle((result, error) -> {
                        final var reqId = id != null ? id.toString() : null;
                        if (error != null) {
                            return toErrorResponse(
                                    reqId,
                                    error instanceof CompletionException && error.getCause() != null ? error.getCause() : error,
                                    request);
                        }
                        return new Response("2.0", reqId, unwrapResult(result, httpRequest), null);
                    }).toCompletableFuture();
        } catch (final RuntimeException re) {
            return completedFuture(toErrorResponse(id == null ? null : id.toString(), re, request));
        }
    }

    @SuppressWarnings("unchecked")
    private Object unwrapResult(final Object result, final Request request) {
        if (result instanceof PartialResponse<?> pr) {
            final var jsonRpcResult = pr.getJsonRpcResult();
            if (request != null && !pr.getHttpResponseHeaders().isEmpty()) {
                var attribute = (Map<String, String>) request.attribute(RESPONSE_HEADERS, Map.class);
                if (attribute == null) {
                    attribute = new ConcurrentHashMap<>();
                    request.setAttribute(RESPONSE_HEADERS, attribute);
                    attribute = request.attribute(RESPONSE_HEADERS, Map.class); // avoid to "synchronized"
                }
                attribute.putAll(pr.getHttpResponseHeaders());
            }
            return jsonRpcResult;
        }
        return result;
    }

    private void appendJsonRpcMethod(final Request httpRequest, final String method) {
        final var existing = httpRequest.attribute(REQUEST_METHOD_ATTRIBUTE, String.class);
        if (existing == null) {
            httpRequest.setAttribute(REQUEST_METHOD_ATTRIBUTE, method);
        } else {
            httpRequest.setAttribute(REQUEST_METHOD_ATTRIBUTE, existing + "," + method);
        }
    }

    public Response toErrorResponse(final String id, final Throwable re, final Object request) {
        final Response.ErrorResponse errorResponse;
        if (re instanceof JsonRpcException jre) {
            errorResponse = new Response.ErrorResponse(jre.code(), re.getMessage(), unwrapResult(jre.data(), null));
        } else {
            errorResponse = new Response.ErrorResponse(-2, re.getMessage(), null);
        }

        if (logger.isLoggable(Level.FINEST)) { // log the stack too, else just the origin method
            logger.log(Level.SEVERE, "An error occured calling /jsonrpc '" + request + "'", re);
        } else {
            logger.log(Level.SEVERE, "An error occured calling /jsonrpc, " +
                    (request instanceof Map<?, ?> map ? "method=" + map.get("method") : request), re);
        }

        return new Response("2.0", id, null, errorResponse);
    }

    public Optional<Response> doValidate(final Map<String, Object> request) {
        final var pair = ensurePresent(request, "jsonrpc", -32600);
        if (pair.second() != null) {
            return of(pair.second());
        }
        if (!"2.0".equals(pair.first())) {
            return of(createResponse(request, -32600, "invalid jsonrpc version"));
        }
        final var method = ensurePresent(request, "method", -32601);
        if (method.second() != null) {
            return of(method.second());
        }
        if (!registry.methods().containsKey(method.first())) {
            return of(createResponse(request, -32601, "Unknown method (" + method.first() + ")"));
        }
        return empty();
    }

    private Tuple2<String, Response> ensurePresent(final Map<String, Object> request, final String key, final int code) {
        final var methodJson = request.get(key);
        if (methodJson == null) {
            return new Tuple2<>(null, createResponse(request, code, "Missing " + key));
        }
        final var method = String.valueOf(methodJson);
        if (method.isEmpty()) {
            return new Tuple2<>(null, createResponse(request, code, "Empty " + key));
        }
        return new Tuple2<>(method, null);
    }

    public Response createResponse(final Object request, final int code, final String message) {
        final var id = request instanceof Map<?, ?> map ? map.get("id") : null;
        return new Response("2.0", id, null, new Response.ErrorResponse(code, message, null));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public CompletionStage<?> execute(final Object request, final Request httpRequest) {
        if (request instanceof Map obj) {
            if (emitter != null) {
                return onBefore(new BeforeRequest(List.of(obj), httpRequest, new ArrayList<>()))
                        .thenCompose(ok -> ok
                                .map(it -> it.get(0))
                                .map(CompletableFuture::completedStage)
                                .orElseGet(() -> handleRequest(obj, httpRequest)));
            }
            return handleRequest(obj, httpRequest);
        }
        if (request instanceof List<?> requests) {
            if (requests.size() > getMaxBulkRequests()) {
                return completedFuture(toErrorResponse(null, new JsonRpcException(
                        10_100, "Too much request at once, limit it to " + getMaxBulkRequests() + " max please.", null), request));
            }

            final var maps = (List<Tuple2<Map<String, Object>, Object>>) requests.stream()
                    .map(it -> it instanceof Map<?, ?> nested ?
                            new Tuple2<Map<String, Object>, Object>((Map<String, Object>) nested, nested) :
                            new Tuple2<Map<String, Object>, Object>(null, it))
                    .toList();

            if (emitter != null) {
                return onBefore(new BeforeRequest(maps.stream().map(Tuple2::first).filter(Objects::nonNull).toList(), httpRequest, new ArrayList<>()))
                        .thenCompose(ok -> ok
                                .map(CompletableFuture::completedFuture)
                                .orElseGet(() -> handleRequests(maps, httpRequest)));
            }
            return handleRequests(maps, httpRequest);
        }
        return completedFuture(createResponse(null, -32600, "Unknown request type: " + request.getClass()));
    }

    protected CompletableFuture<List<Response>> handleRequests(final List<Tuple2<Map<String, Object>, Object>> requests,
                                                               final Request httpRequest) {
        final CompletableFuture<?>[] futures = requests.stream()
                .map(it -> it.first() != null ?
                        handleRequest(it.first(), httpRequest) :
                        completedFuture(createResponse(it.second(), -32600, "Batch requests must be JSON objects")))
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture
                .allOf(futures)
                .thenApply(ignored -> Stream.of(futures)
                        .map(f -> (Response) f.getNow(null))
                        .toList());
    }

    protected CompletionStage<Optional<List<Response>>> onBefore(final BeforeRequest event) {
        emitter.emit(event);
        return (switch (event.promises().size()) {
            case 0 -> alwaysReady;
            case 1 -> event.promises().get(0).toCompletableFuture();
            default -> CompletableFuture.allOf(event.promises().toArray(new CompletableFuture<?>[0]));
        }).thenApply(ready -> Optional.<List<Response>>empty()).exceptionally(ex -> {
            if (ex instanceof JsonRpcException jre) {
                final var req = event.requests().iterator();
                return of(event.requests().stream()
                        .map(r -> {
                            final var next = req.next();
                            return toErrorResponse(next != null && next.get("id") instanceof String id ? id : null, jre, next);
                        })
                        .toList());
            }
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(ex);
        });
    }

    protected int getMaxBulkRequests() {
        return 50;
    }

    protected record Tuple2<A, B>(A first, B second) {
    }
}
