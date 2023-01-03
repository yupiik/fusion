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
package io.yupiik.fusion.kubernetes.client;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class KubernetesClientTest {
    @Test
    void call(@TempDir final Path work) throws IOException {
        final var token = work.resolve("token");
        Files.writeString(token, "foo");

        final var certs = work.resolve("certs");
        try (final var pem = requireNonNull(Thread.currentThread()
                .getContextClassLoader().getResourceAsStream("test.pem"))) {
            Files.copy(pem, certs);
        }

        try (
                final var server = new Server(ex -> {
                    final var ok = "Bearer foo".equals(ex.getRequestHeaders().getFirst("authorization")) &&
                            "/foo?test".equals(ex.getRequestURI().toASCIIString());
                    final var body = ("{\"ok\":" + ok + "}").getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(ok ? 200 : 500, body.length);
                    ex.getResponseBody().write(body);
                    ex.close();
                });
                final var http = new KubernetesClient(new KubernetesClientConfiguration()
                        .setMaster(server.base().toASCIIString())
                        .setToken(token.toString())
                        .setCertificates(certs.toString()))) {
            final var response = http.send(HttpRequest.newBuilder().GET()
                    .uri(URI.create("https://kubernetes.api/foo?test"))
                    .build());
            assertEquals(200, response.statusCode());
        }
    }

    private static class Server implements AutoCloseable {
        private final HttpsServer server;

        private Server(final HttpHandler httpHandler) {
            try {
                this.server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
                this.server.setHttpsConfigurator(new HttpsConfigurator(createSSLContext()));
                this.server.createContext("/").setHandler(httpHandler);
                this.server.start();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private SSLContext createSSLContext() {
            try (final var p12 = requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream("test.p12"))) {
                final var keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(p12, "changeit".toCharArray());

                final var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keyStore);

                final var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, "changeit".toCharArray());

                final var sc = SSLContext.getInstance("TLS");
                sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
                return sc;
            } catch (final Exception e) {
                return fail(e);
            }
        }

        private URI base() {
            return URI.create("https://localhost:" + server.getAddress().getPort());
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
