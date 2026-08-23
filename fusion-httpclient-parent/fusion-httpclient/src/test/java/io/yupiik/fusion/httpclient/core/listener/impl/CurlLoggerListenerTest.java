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

import io.yupiik.fusion.httpclient.core.request.UnlockedHttpRequest;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurlLoggerListenerTest {
    @Test
    void getWithoutPayload() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger("test")));
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                HttpHeaders.of(Map.of("Accept", List.of("*/*")), (a, b) -> true));
        assertEquals(
                "curl -X GET 'http://localhost:1234/test1' \\\n" +
                        "  -H 'Accept: */*'",
                listener.toLogMessage(request, null, null, response(200, request)));
    }

    @Test
    void postWithPayloadAndSingleQuote() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger("test")));
        final var request = new UnlockedHttpRequest(
                "POST", URI.create("http://localhost:1234/test1"),
                HttpRequest.BodyPublishers.ofString("{\"name\":\"it's\"}"),
                HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (a, b) -> true));
        assertEquals(
                "curl -X POST 'http://localhost:1234/test1' \\\n" +
                        "  -H 'Content-Type: application/json' \\\n" +
                        "  --data-raw '{\"name\":\"it'\\''s\"}'",
                listener.toLogMessage(request, "{\"name\":\"it's\"}".getBytes(UTF_8), null, response(200, request)));
    }

    @Test
    void defaultSensitiveHeadersAreMasked() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger("test")));
        final var headers = new LinkedHashMap<String, List<String>>();
        headers.put("Authorization", List.of("Bearer secret"));
        headers.put("X-API-Key", List.of("key-value"));
        headers.put("Set-Cookie", List.of("session=abc"));
        headers.put("Accept", List.of("*/*"));
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                HttpHeaders.of(headers, (a, b) -> true));
        assertEquals(
                "curl -X GET 'http://localhost:1234/test1' \\\n" +
                        "  -H 'Accept: */*' \\\n" +
                        "  -H 'Authorization: ******' \\\n" +
                        "  -H 'Set-Cookie: ******' \\\n" +
                        "  -H 'X-API-Key: ******'",
                listener.toLogMessage(request, null, null, response(200, request)));
    }

    @Test
    void emptySensitiveHeadersDisableMasking() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger("test"))
                .setSensitiveHeaders(Set.of()));
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                HttpHeaders.of(Map.of("Authorization", List.of("Bearer secret")), (a, b) -> true));
        assertEquals(
                "curl -X GET 'http://localhost:1234/test1' \\\n" +
                        "  -H 'Authorization: Bearer secret'",
                listener.toLogMessage(request, null, null, response(200, request)));
    }

    @Test
    void sensitiveRequestHeadersAreMasked() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(
                Logger.getLogger("test")).setSensitiveHeaders(List.of("Authorization", "X-API-Key")));
        final var headers = new LinkedHashMap<String, List<String>>();
        headers.put("Authorization", List.of("Bearer secret"));
        headers.put("x-api-key", List.of("key-value"));
        headers.put("Accept", List.of("*/*"));
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                HttpHeaders.of(headers, (a, b) -> true));
        assertEquals(
                "curl -X GET 'http://localhost:1234/test1' \\\n" +
                        "  -H 'Accept: */*' \\\n" +
                        "  -H 'Authorization: ******' \\\n" +
                        "  -H 'x-api-key: ******'",
                listener.toLogMessage(request, null, null, response(200, request)));
    }

    @Test
    void sensitiveResponseHeadersAreMasked() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(
                Logger.getLogger("test"))
                .setLogPayload(true)
                .setSensitiveHeaders(Set.of("set-cookie")));
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                HttpHeaders.of(Map.of(), (a, b) -> true));
        assertEquals(
                "curl -i -X GET 'http://localhost:1234/test1'\n" +
                        "HTTP/1.1 200\n" +
                        "Set-Cookie: ******\n" +
                        "\n" +
                        "",
                listener.toLogMessage(request, null, null,
                        response(200, request, Map.of("Set-Cookie", List.of("session=abc")), "")));
    }

    @Test
    void configurationRequiresALogger() {
        assertThrows(NullPointerException.class, () -> new CurlLoggerListener.Configuration(null));
        assertThrows(NullPointerException.class, () -> new CurlLoggerListener(null));
    }

    @Test
    void responseIsFormattedLikeCurlOutput() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger("test")).setLogPayload(true));
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                HttpHeaders.of(Map.of(), (a, b) -> true));
        assertEquals(
                "curl -i -X GET 'http://localhost:1234/test1'\n" +
                        "HTTP/1.1 200\n" +
                        "Content-Type: application/json\n" +
                        "\n" +
                        "{\"ok\":true}",
                listener.toLogMessage(request, null, null,
                        response(200, request, Map.of("Content-Type", List.of("application/json")), "{\"ok\":true}")));
    }

    @Test
    void responseWithoutHeadersIsFormattedLikeCurlOutput() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger("test")).setLogPayload(true));
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                HttpHeaders.of(Map.of(), (a, b) -> true));
        assertEquals(
                "curl -i -X GET 'http://localhost:1234/test1'\n" +
                        "HTTP/1.1 200\n" +
                        "\n" +
                        "{\"ok\":true}",
                listener.toLogMessage(request, null, null, response(200, request, "{\"ok\":true}")));
    }

    @Test
    void errorIsLoggedLikeCurl() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger("test")).setLogPayload(true));
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                HttpHeaders.of(Map.of(), (a, b) -> true));
        assertEquals(
                "curl -i -X GET 'http://localhost:1234/test1'\n" +
                        "curl: boom",
                listener.toLogMessage(request, null, new IllegalStateException("boom"), null));
    }

    @Test
    void beforeKeepsBodyReusable() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger("test")).setLogPayload(true));
        final var request = new UnlockedHttpRequest(
                "POST", URI.create("http://localhost:1234/test1"),
                HttpRequest.BodyPublishers.ofString("payload"),
                HttpHeaders.of(Map.of(), (a, b) -> true));
        final var state = listener.before(1, request);
        assertEquals("payload", readBody(state.request()));
        assertEquals("payload", readBody(state.request()));
    }

    @Test
    void fullFlowLogsThroughLogger() {
        final var logger = Logger.getLogger("curl-full-flow-" + System.nanoTime());
        final var capture = new LogCapture();
        logger.addHandler(capture);

        try {
            final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(logger).setLogPayload(true));
            final var request = new UnlockedHttpRequest(
                    "GET", URI.create("http://localhost:1234/test1"),
                    HttpHeaders.of(Map.of(), (a, b) -> true));
            final var state = listener.before(1, request);
            listener.after(state.state(), state.request(), null, response(200, request, ""));
            assertEquals(List.of(
                    "curl -i -X GET 'http://localhost:1234/test1'\n" +
                            "HTTP/1.1 200\n" +
                            "\n" +
                            ""), capture.messages);
        } finally {
            logger.removeHandler(capture);
        }
    }

    @Test
    void disabledLoggerDoesNotEvaluateMessage() {
        final var logger = Logger.getLogger("curl-disabled-" + System.nanoTime());
        logger.setLevel(Level.OFF);
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(logger).setLogPayload(false));
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                HttpHeaders.of(Map.of(), (a, b) -> true));
        listener.after(listener.before(1, request).state(), request, null, response(200, request, ""));
    }

    @Test
    void failingBodyPublisherIsWrapped() {
        final var listener = new CurlLoggerListener(new CurlLoggerListener.Configuration(Logger.getLogger("test")));
        final var failing = new HttpRequest.BodyPublisher() {
            @Override
            public long contentLength() {
                return 1;
            }

            @Override
            public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
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
                subscriber.onError(new java.io.IOException("boom"));
            }
        };
        final var request = new UnlockedHttpRequest(
                "GET", URI.create("http://localhost:1234/test1"),
                failing,
                HttpHeaders.of(Map.of(), (a, b) -> true));
        final var error = assertThrows(IllegalStateException.class, () -> listener.before(1, request));
        assertEquals("boom", error.getCause().getMessage());
    }

    private String readBody(final HttpRequest request) {
        final var subscriber = HttpResponse.BodySubscribers.ofByteArray();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
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
        return new String(subscriber.getBody().toCompletableFuture().join(), UTF_8);
    }

    private HttpResponse<String> response(final int status, final HttpRequest request) {
        return response(status, request, null, "");
    }

    private HttpResponse<String> response(final int status, final HttpRequest request, final String body) {
        return response(status, request, null, body);
    }

    private HttpResponse<String> response(final int status, final HttpRequest request,
                                          final Map<String, List<String>> headers, final String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return headers == null ? null : HttpHeaders.of(headers, (a, b) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static final class LogCapture extends Handler {
        private final List<String> messages = new java.util.ArrayList<>();

        @Override
        public void publish(final LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
