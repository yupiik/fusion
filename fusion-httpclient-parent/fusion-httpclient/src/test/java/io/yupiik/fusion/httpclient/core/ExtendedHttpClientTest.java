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
package io.yupiik.fusion.httpclient.core;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.httpclient.core.listener.RequestListener;
import io.yupiik.fusion.httpclient.core.listener.impl.DefaultTimeout;
import io.yupiik.fusion.httpclient.core.listener.impl.SetUserAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class ExtendedHttpClientTest {
    @Test
    void send() {
        try (
                final var server = new Server(ex -> {
                    final var body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(200, body.length);
                    ex.getResponseBody().write(body);
                    ex.close();
                });
                final var http = newClient()) {
            assertEquals("{\"ok\":true}", http.send(server.GET().build()).body());
            assertEquals(1, http.getRequestCount());
        }
    }

    @Test
    void timeout() {
        try (
                final var server = new Server(ex -> {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    ex.sendResponseHeaders(200, 0);
                    ex.close();
                });
                final var http = newClient(new DefaultTimeout(Duration.ofMillis(100)))) {
            assertInstanceOf(
                    HttpTimeoutException.class,
                    assertThrows(
                            IllegalStateException.class,
                            () -> http.send(server.GET().build()))
                            .getCause());
        }
    }

    @Test
    void userAgent() {
        try (
                final var server = new Server(ex -> {
                    ex.sendResponseHeaders("test/1.0".equals(ex.getRequestHeaders().getFirst("user-agent")) ? 200 : 500, 0);
                    ex.close();
                });
                final var http = newClient(new SetUserAgent("test/1.0"))) {
            assertEquals(200, http.send(server.GET().build()).statusCode());
        }
    }

    @Test
    void closeWithProvidedDelegateKeepsTheTransportAlive() throws Exception {
        // when a delegate is provided by the caller, close() must not touch the transport,
        // regardless of the closeTimeout (the caller owns the lifecycle)
        final var transportClosed = new AtomicBoolean(false);
        final var tracked = new TrackedCloseHttpClient(() -> transportClosed.set(true));
        final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration()
                .setDelegate(tracked)
                .setCloseTimeout(Duration.ofMillis(100)));
        client.close();
        assertFalse(transportClosed.get(), "a caller-provided delegate must not be closed by ExtendedHttpClient.close()");
    }

    @Test
    void closeWithoutTimeoutDoesNotCloseAnOwnedTransport() throws Exception {
        assumeTrue(Runtime.version().feature() >= 21, "requires Java 21+");
        try (
                final var server = new Server(ex -> {
                    final var body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(200, body.length);
                    ex.getResponseBody().write(body);
                    ex.close();
                });
                final var client = new ExtendedHttpClient(new ExtendedHttpClientConfiguration())) {
            assertEquals("{\"ok\":true}", client.send(server.GET().build()).body());
            client.close(); // no closeTimeout: the owned transport must stay alive
            // observable proofs: the transport still serves requests and is not terminated
            assertEquals("{\"ok\":true}", client.send(server.GET().build()).body());
            assertFalse((boolean) HttpClient.class.getMethod("isTerminated").invoke(client.delegate()));
        }
    }

    private ExtendedHttpClient newClient(final RequestListener<?>... listeners) {
        return new ExtendedHttpClient(new ExtendedHttpClientConfiguration()
                .setRequestListeners(List.of(listeners)));
    }

    private static class Server implements AutoCloseable {
        private final HttpServer server;

        private Server(final HttpHandler httpHandler) {
            try {
                this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                this.server.createContext("/").setHandler(httpHandler);
                this.server.start();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private URI base() {
            return URI.create("http://localhost:" + server.getAddress().getPort());
        }

        private HttpRequest.Builder GET() {
            return HttpRequest.newBuilder().GET().uri(base());
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static class TrackedCloseHttpClient extends HttpClient implements AutoCloseable {
        private final Runnable onClose;

        private TrackedCloseHttpClient(final Runnable onClose) {
            this.onClose = onClose;
        }

        @Override
        public void close() {
            onClose.run();
        }

        // stubs for the abstract methods, never used by these tests
        @Override
        public java.util.Optional<java.net.CookieHandler> cookieHandler() {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<Duration> connectTimeout() {
            return java.util.Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public java.util.Optional<java.net.ProxySelector> proxy() {
            return java.util.Optional.empty();
        }

        @Override
        public javax.net.ssl.SSLContext sslContext() {
            return null;
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            return null;
        }

        @Override
        public java.util.Optional<java.net.Authenticator> authenticator() {
            return java.util.Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public java.util.Optional<java.util.concurrent.Executor> executor() {
            return java.util.Optional.empty();
        }

        @Override
        public <T> java.net.http.HttpResponse<T> send(final java.net.http.HttpRequest request,
                                                      final java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) {
            return null;
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                final java.net.http.HttpRequest request,
                final java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) {
            return null;
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(
                final java.net.http.HttpRequest request,
                final java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler,
                final java.net.http.HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return null;
        }
    }
}
