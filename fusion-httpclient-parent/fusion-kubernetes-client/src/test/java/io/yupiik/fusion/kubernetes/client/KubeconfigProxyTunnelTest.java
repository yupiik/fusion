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
package io.yupiik.fusion.kubernetes.client;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeconfigProxyTunnelTest {
    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    @Timeout(30)
    void separatesProxyAndOriginCredentialsInHttpsTunnel(final String format, @TempDir final Path work) throws Exception {
        // Surefire must set -Djdk.http.auth.tunneling.disabledSchemes= before the HTTP client is initialized.
        // Deliberately do not change JVM-wide authentication properties here (tests run in parallel).
        final var expectedProxyAuthorization = "Basic " + Base64.getEncoder()
                .encodeToString("u@ser+name:p:ss+word".getBytes(UTF_8));
        final var originHeaders = new CompletableFuture<Map<String, String>>();
        final var origin = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        try {
            origin.setHttpsConfigurator(new HttpsConfigurator(serverSslContext()));
            origin.createContext("/test", exchange -> {
                try (exchange) {
                    final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, String.join(",", values)));
                    originHeaders.complete(headers);
                    final var body = "through CONNECT".getBytes(UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } catch (final IOException | RuntimeException e) {
                    originHeaders.completeExceptionally(e);
                    throw e;
                }
            });
            origin.start();
            try (final var proxy = new TunnelProxy(origin.getAddress(), expectedProxyAuthorization)) {
                final var config = configuration(work, format, origin.getAddress().getPort(), proxy.port());
                try (final var client = new KubernetesClient(config)) {
                    final var response = client.sendAsync(HttpRequest.newBuilder(URI.create("https://kubernetes.api/test"))
                                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
                    final var connect = proxy.connectRequest.get(10, TimeUnit.SECONDS);
                    assertEquals("CONNECT localhost:" + origin.getAddress().getPort() + " HTTP/1.1", connect.requestLine());
                    assertEquals(expectedProxyAuthorization, connect.headers().get("Proxy-Authorization"));
                    assertNull(connect.headers().get("Authorization"), "CONNECT must not expose the Kubernetes bearer token");

                    final var result = response.get(10, TimeUnit.SECONDS);
                    assertEquals(200, result.statusCode());
                    assertEquals("through CONNECT", result.body());
                    final var headers = originHeaders.get(10, TimeUnit.SECONDS);
                    assertEquals("Bearer api-token", headers.get("Authorization"));
                    assertNull(headers.get("Proxy-Authorization"), "Proxy credentials must not reach the HTTPS origin");
                    assertTrue(client.authenticator().isEmpty(), "Preemptive proxy authentication must not install an Authenticator");
                }
            }
        } finally {
            origin.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    @Timeout(30)
    void separatesProxyAndOriginCredentialsForWebSocket(final String format, @TempDir final Path work) throws Exception {
        final var explicit = "json".equals(format);
        final var expectedProxyAuthorization = "Basic " + Base64.getEncoder()
                .encodeToString((explicit ? "explicit:proxy" : "u@ser+name:p:ss+word").getBytes(UTF_8));
        final var expectedAuthorization = explicit ? "Bearer explicit-origin" : "Bearer api-token";
        final var executor = Executors.newSingleThreadExecutor();
        try (final var origin = (SSLServerSocket) serverSslContext().getServerSocketFactory().createServerSocket()) {
            origin.bind(new InetSocketAddress("localhost", 0));
            origin.setSoTimeout(10_000);
            final var originHeaders = new CompletableFuture<Map<String, String>>();
            final var handshake = executor.submit(() -> {
                try (final var socket = origin.accept()) {
                    socket.setSoTimeout(10_000);
                    final var request = TunnelProxy.readHead(socket.getInputStream());
                    originHeaders.complete(request.headers());
                    final var accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                            .digest((request.headers().get("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                    .getBytes(ISO_8859_1)));
                    socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\n" +
                            "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                            "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(ISO_8859_1));
                    socket.getOutputStream().flush();
                    // Wait until the client aborts the established WebSocket before closing TLS.
                    socket.getInputStream().read();
                } catch (final Exception e) {
                    originHeaders.completeExceptionally(e);
                    throw e;
                }
                return null;
            });
            try (final var proxy = new TunnelProxy((InetSocketAddress) origin.getLocalSocketAddress(), expectedProxyAuthorization)) {
                final var config = configuration(work, format, origin.getLocalPort(), proxy.port());
                try (final var client = new KubernetesClient(config)) {
                    final var builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10));
                    if (explicit) {
                        builder.header("Proxy-Authorization", expectedProxyAuthorization).header("Authorization", expectedAuthorization);
                    }
                    final var webSocket = builder.buildAsync(URI.create("wss://kubernetes.api/test"), new WebSocket.Listener() {})
                            .get(10, TimeUnit.SECONDS);
                    try {
                        final var connect = proxy.connectRequest.get(10, TimeUnit.SECONDS);
                        assertEquals(expectedProxyAuthorization, connect.headers().get("Proxy-Authorization"));
                        assertNull(connect.headers().get("Authorization"));
                        final var headers = originHeaders.get(10, TimeUnit.SECONDS);
                        assertEquals(expectedAuthorization, headers.get("Authorization"));
                        assertNull(headers.get("Proxy-Authorization"));
                    } finally {
                        webSocket.abort();
                    }
                }
                handshake.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "WebSocket origin must terminate");
        }
    }

    private KubernetesClientConfiguration configuration(final Path work, final String format,
                                                         final int originPort, final int proxyPort) throws IOException {
        final var path = work.resolve("tunnel." + format);
        final var server = "https://localhost:" + originPort;
        final var proxy = "http://u%40ser+name:p%3Ass%2Bword@127.0.0.1:" + proxyPort;
        final String certificate;
        try (final var pem = requireNonNull(getClass().getResourceAsStream("/test.pem"))) {
            certificate = Base64.getEncoder().encodeToString(pem.readAllBytes());
        }
        Files.writeString(path, "json".equals(format) ? """
                {
                  "current-context": "local",
                  "contexts": [{"name": "local", "context": {"cluster": "local"}}],
                  "clusters": [{"name": "local", "cluster": {
                    "server": "%s", "proxy-url": "%s", "certificate-authority-data": "%s"
                  }}]
                }
                """.formatted(server, proxy, certificate) : """
                current-context: local
                contexts:
                - name: local
                  context:
                    cluster: local
                clusters:
                - name: local
                  cluster:
                    server: %s
                    proxy-url: "%s"
                    certificate-authority-data: %s
                """.formatted(server, proxy, certificate));
        return new KubernetesClientConfiguration().setKubeconfig(path).setToken("none")
                .setTokenEvaluator(() -> "api-token")
                .setClientCustomizer(builder -> builder.connectTimeout(Duration.ofSeconds(5)));
    }

    private SSLContext serverSslContext() throws Exception {
        final var store = KeyStore.getInstance("PKCS12");
        try (final var p12 = requireNonNull(getClass().getResourceAsStream("/test.p12"))) {
            store.load(p12, "changeit".toCharArray());
        }
        final var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, "changeit".toCharArray());
        final var context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);
        return context;
    }

    private record RequestHead(String requestLine, Map<String, String> headers) {
    }

    private static final class TunnelProxy implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService executor = Executors.newFixedThreadPool(2);
        private final CompletableFuture<RequestHead> connectRequest = new CompletableFuture<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final List<Socket> sockets = new ArrayList<>();
        private boolean closed;

        private TunnelProxy(final InetSocketAddress origin, final String expectedAuthorization) throws IOException {
            listener = new ServerSocket();
            try {
                listener.bind(new InetSocketAddress("127.0.0.1", 0));
                listener.setSoTimeout(10_000);
            } catch (final IOException e) {
                listener.close();
                executor.shutdownNow();
                throw e;
            }
            executor.submit(() -> {
                try (final var downstream = register(listener.accept());
                     final var upstream = register(new Socket())) {
                    downstream.setSoTimeout(10_000);
                    final var head = readHead(downstream.getInputStream());
                    connectRequest.complete(head);
                    if (!expectedAuthorization.equals(head.headers().get("Proxy-Authorization"))) {
                        downstream.getOutputStream().write(("HTTP/1.1 407 Proxy Authentication Required\r\n" +
                                "Proxy-Authenticate: Basic realm=\"tunnel-test\"\r\n" +
                                "Content-Length: 0\r\nConnection: close\r\n\r\n").getBytes(ISO_8859_1));
                        return null;
                    }
                    upstream.connect(origin, 5_000);
                    upstream.setSoTimeout(10_000);
                    downstream.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(ISO_8859_1));
                    downstream.getOutputStream().flush();
                    // Read the head byte-by-byte so that no buffered reader consumes TLS bytes.
                    executor.submit(() -> {
                        try {
                            downstream.getInputStream().transferTo(upstream.getOutputStream());
                            upstream.shutdownOutput();
                        } catch (final IOException e) {
                            // Closing the fixture interrupts the tunnel in either direction.
                        }
                    });
                    upstream.getInputStream().transferTo(downstream.getOutputStream());
                } catch (final IOException e) {
                    connectRequest.completeExceptionally(e);
                }
                return null;
            });
        }

        private int port() {
            return listener.getLocalPort();
        }

        private Socket register(final Socket socket) throws IOException {
            lock.lock();
            try {
                if (closed) {
                    socket.close();
                    throw new IOException("Tunnel proxy is closed");
                }
                sockets.add(socket);
                return socket;
            } finally {
                lock.unlock();
            }
        }

        private static RequestHead readHead(final InputStream input) throws IOException {
            final var bytes = new ByteArrayOutputStream();
            int end = 0;
            while (bytes.size() < 16_384) {
                final var next = input.read();
                if (next < 0) {
                    throw new IOException("Unexpected EOF reading CONNECT headers");
                }
                bytes.write(next);
                end = (end << 8) | next;
                if (end == 0x0d0a0d0a) {
                    final var lines = bytes.toString(ISO_8859_1).split("\r\n");
                    final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    for (int i = 1; i < lines.length; i++) {
                        final var colon = lines[i].indexOf(':');
                        if (colon < 1) {
                            throw new IOException("Malformed CONNECT header");
                        }
                        headers.merge(lines[i].substring(0, colon), lines[i].substring(colon + 1).strip(),
                                (first, second) -> first + "," + second);
                    }
                    return new RequestHead(lines[0], headers);
                }
            }
            throw new IOException("CONNECT headers exceed test limit");
        }

        @Override
        public void close() throws Exception {
            try {
                listener.close();
            } finally {
                lock.lock();
                try {
                    closed = true;
                    for (final var socket : sockets) {
                        try {
                            socket.close();
                        } catch (final IOException e) {
                            // Continue closing the other half of the tunnel.
                        }
                    }
                } finally {
                    lock.unlock();
                    executor.shutdownNow();
                }
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Tunnel workers must terminate");
            }
        }
    }
}
