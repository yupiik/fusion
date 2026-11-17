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

import io.yupiik.fusion.httpclient.core.DelegatingHttpClient;
import io.yupiik.fusion.httpclient.core.response.StaticHttpResponse;
import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpResponse.BodySubscribers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class TestClientTest {
    @ParameterizedTest
    @ValueSource(strings = {"5", "\"5\""})
    void numericResult(final String result) {
        try (final var mapper = new JsonMapperImpl(List.of(), c -> empty());
             final var client = newLocalClient(mapper, "{\"jsonrpc\":\"2.0\",\"result\":" + result + "}")) {
            final var response = client.jsonRpcRequest("test", Map.of()).asJsonRpc().success();
            assertEquals(5, response.as(Integer.class));
            assertEquals(5L, response.as(Long.class));
            assertEquals(5.0, response.as(Double.class));
            assertThrows(JsonException.class, () -> mapper.fromString(Integer.class, "\"5\""));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"[5,6]", "[\"5\",\"6\"]"})
    void numericListResult(final String result) {
        try (final var mapper = new JsonMapperImpl(List.of(), c -> empty());
             final var client = newLocalClient(mapper, "{\"jsonrpc\":\"2.0\",\"result\":" + result + "}")) {
            final var response = client.jsonRpcRequest("test", Map.of()).asJsonRpc().success();
            assertEquals(List.of(5, 6), response.asList(Integer.class));
            assertEquals(List.of(5L, 6L), response.asList(Long.class));
            assertEquals(List.of(5.0, 6.0), response.asList(Double.class));
            assertThrows(JsonException.class, () -> mapper.fromString(Integer.class, "\"5\""));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonRpcSingleRequest() {
        try (final var client = newLocalClient()) {
            final var result = (Map<String, Object>) client
                    .jsonRpcRequest("test", Map.of("id", "1234"))
                    .asJsonRpc()
                    .success()
                    .as(Object.class);
            assertEquals(Map.of("jsonrpc", "2.0", "method", "test", "params", Map.of("id", "1234")), result);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonRpcBulkRequest() {
        try (final var client = newLocalClient()) {
            final var bulk = List.of(
                    client.jsonRpcRequestPayload("test1", Map.of("id", "1234")),
                    client.jsonRpcRequestPayload("test2", Map.of("id", "5678")));
            final var result = client
                    .jsonRpcRequest(bulk)
                    .asJsonRpc()
                    .success()
                    .asList(Object.class);
            assertEquals(
                    List.of(
                            Map.of("jsonrpc", "2.0", "method", "test1", "params", Map.of("id", "1234")),
                            Map.of("jsonrpc", "2.0", "method", "test2", "params", Map.of("id", "5678"))),
                    result);
        }
    }

    private TestClient newLocalClient() {
        return newLocalClient(new JsonMapperImpl(List.of(), c -> empty()), null);
    }

    private TestClient newLocalClient(final JsonMapperImpl mapper, final String response) {
        return new TestClient(
                new DelegatingHttpClient(null) {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
                        return new StaticHttpResponse<>(
                                request, request.uri(), HTTP_1_1, 200,
                                HttpHeaders.of(Map.of(), (a, b) -> true),
                                (T) (response == null ? wrapRequest(request.bodyPublisher().orElseThrow()) : response));
                    }
                },
                mapper, URI.create("http://testing"));
    }

    private String wrapRequest(final HttpRequest.BodyPublisher bodyPublisher) {
        final var handler = ofString(UTF_8);
        bodyPublisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                handler.onSubscribe(subscription);
            }

            @Override
            public void onNext(final ByteBuffer item) {
                handler.onNext(List.of(item));
            }

            @Override
            public void onError(final Throwable throwable) {
                handler.onError(throwable);
            }

            @Override
            public void onComplete() {
                handler.onComplete();
            }
        });
        try {
            return "{\"jsonrpc\":\"2.0\",\"result\":" + handler.getBody().toCompletableFuture().get() + "}";
        } catch (final InterruptedException | ExecutionException e) {
            return fail(e);
        }
    }
}
