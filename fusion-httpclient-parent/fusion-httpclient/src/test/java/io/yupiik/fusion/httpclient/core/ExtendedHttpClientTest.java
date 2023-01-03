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
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
