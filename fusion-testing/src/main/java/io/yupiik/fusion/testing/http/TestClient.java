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
package io.yupiik.fusion.testing.http;

import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.httpclient.core.ExtendedHttpClient;
import io.yupiik.fusion.httpclient.core.ExtendedHttpClientConfiguration;
import io.yupiik.fusion.httpclient.core.listener.impl.ExchangeLogger;
import io.yupiik.fusion.httpclient.core.response.StaticHttpResponse;
import io.yupiik.fusion.json.JsonMapper;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.time.Clock.systemUTC;
import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Simple {@link HttpClient} wrapper which will provide the base uri of the server,
 * avoid exceptions and helps to deserialize JSON-RPC responses.
 * <p>
 * By default its related bean (auto-deployed) depends on an embedded fusion-http-server and fusion-json.
 */
public class TestClient implements AutoCloseable {
    private final HttpClient client;
    private final URI uri;
    private final JsonMapper json;

    public TestClient(final HttpClient client, final JsonMapper jsonMapper, final URI uri) {
        this.client = client;
        this.uri = uri;
        this.json = jsonMapper;
    }

    /**
     * Similar to a plain {@link HttpClient#send(HttpRequest, HttpResponse.BodyHandler)} call
     * but with a relative URI builder and expected result type (JSON).
     */
    public <T> HttpResponse<T> send(final Function<URI, HttpRequest> requestBuilder,
                                    final Class<T> result) {
        return send(requestBuilder, (Type) result);
    }

    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(final Function<URI, HttpRequest> requestBuilder,
                                    final Type result) {
        try {
            final var res = client.send(requestBuilder.apply(uri), ofString());
            if (result == String.class) {
                return (HttpResponse<T>) res;
            }
            return new StaticHttpResponse<>(
                    res.request(), res.uri(), res.version(),
                    res.statusCode(), res.headers(),
                    json.fromString(result, res.body()));
        } catch (final IOException e) {
            return fail(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(e);
        }
    }

    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) {
        try {
            return client.send(request, responseBodyHandler);
        } catch (final IOException e) {
            return fail(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(e);
        }
    }

    public EnrichedResponse jsonRpcRequest(final String method, final Object payload) {
        return jsonRpcRequest(jsonRpcRequestPayload(method, payload));
    }

    public EnrichedResponse jsonRpcRequest(final Object requests) {
        return new EnrichedResponse(
                send(
                        HttpRequest.newBuilder()
                                .POST(HttpRequest.BodyPublishers.ofString(json.toString(requests)))
                                .uri(uri.resolve("/jsonrpc"))
                                .build(),
                        ofString()),
                json);
    }

    public Map<String, Object> jsonRpcRequestPayload(final String method, final Object payload) {
        return Map.of(
                "jsonrpc", "2.0",
                "method", method,
                "params", payload);
    }

    @Override
    public void close() {
        if (AutoCloseable.class.isInstance(client)) { // until we move to java 21 use this pattern
            try {
                ((AutoCloseable) client).close();
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static class JsonRpc {
        private final Map<String, Object> response;
        private final JsonMapper jsonMapper;

        public JsonRpc(final Map<String, Object> response, final JsonMapper jsonMapper) {
            this.response = response;
            this.jsonMapper = jsonMapper;
        }

        public Map<String, Object> raw() {
            return response;
        }

        public <T> T as(final Class<T> type) {
            try {
                final var data = response.get("result");
                if (data == null) {
                    return null;
                }
                return jsonMapper.fromString(type, jsonMapper.toString(data()));
            } catch (final IllegalStateException ise) {
                return fail(response.toString(), ise);
            }
        }

        public <T> List<T> asList(final Class<T> type) {
            success(); // error can't be a list so enforce a success
            final var result = response.get("result");
            if (result == null) {
                return null;
            }
            return jsonMapper.fromString(new Types.ParameterizedTypeImpl(List.class, type), jsonMapper.toString(result));
        }

        public JsonRpc success() {
            assertNull(response.get("error"), response::toString);
            return this;
        }

        public JsonRpc failure() {
            assertNotNull(response.get("error"), response::toString);
            return this;
        }

        private Object data() {
            return ofNullable(response.get("error")).orElseGet(() -> response.get("result"));
        }
    }

    /**
     * A plain {@link HttpResponse<String>} with JSON-RPC helpers.
     */
    public static class EnrichedResponse implements HttpResponse<String> {
        private final HttpResponse<String> original;
        private final JsonMapper jsonMapper;

        private EnrichedResponse(final HttpResponse<String> original,
                                 final JsonMapper jsonMapper) {
            this.original = original;
            this.jsonMapper = jsonMapper;
        }

        /**
         * @return a helper to validate single JSON-RPC request.
         */
        @SuppressWarnings("unchecked")
        public JsonRpc asJsonRpc() {
            return new JsonRpc((Map<String, Object>) jsonMapper.fromString(Object.class, body()), jsonMapper);
        }

        /**
         * @return a helper to validate bulk JSON-RPC requests.
         */
        @SuppressWarnings("unchecked")
        public List<JsonRpc> jsonRpcs() {
            return ((List<Map<String, Object>>) jsonMapper.fromString(Object.class, body())).stream()
                    .map(it -> new JsonRpc(it, jsonMapper))
                    .toList();
        }

        @Override
        public String body() {
            return original.body();
        }

        @Override
        public int statusCode() {
            return original.statusCode();
        }

        @Override
        public HttpRequest request() {
            return original.request();
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return original.previousResponse();
        }

        @Override
        public HttpHeaders headers() {
            return original.headers();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return original.sslSession();
        }

        @Override
        public URI uri() {
            return original.uri();
        }

        @Override
        public HttpClient.Version version() {
            return original.version();
        }
    }

    // indirection to not load the ExtendedHttpClient upfront
    public static class HttpClients {
        private HttpClients() {
            // no-op
        }

        public static HttpClient create() {
            final var client = newHttpClient();
            if (Boolean.getBoolean("fusion.testing.http.plainHttpClient")) {
                return client;
            }
            try { // try testing flavor
                return new ExtendedHttpClient(new ExtendedHttpClientConfiguration()
                        .setDelegate(client)
                        .setRequestListeners(List.of(new ExchangeLogger(Logger.getLogger(TestClient.class.getName()), systemUTC(), true))));
            } catch (final Throwable t) {
                return client;
            }
        }
    }
}
