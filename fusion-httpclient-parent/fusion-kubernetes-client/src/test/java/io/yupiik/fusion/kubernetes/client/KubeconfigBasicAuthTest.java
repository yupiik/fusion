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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KubeconfigBasicAuthTest {
    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void preemptiveBasicForAllTransports(final String format, @TempDir final Path work) throws Exception {
        final var received = new CopyOnWriteArrayList<List<String>>();
        final var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            received.add(List.copyOf(exchange.getRequestHeaders().get("Authorization")));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try (final var client = new KubernetesClient(configuration(work, format,
                "http://localhost:" + server.getAddress().getPort(), ""))) {
            final var request = HttpRequest.newBuilder(URI.create("http://kubernetes.api/test")).build();
            final var explicit = HttpRequest.newBuilder(request.uri()).header("aUtHoRiZaTiOn", "Custom value").build();
            assertEquals(200, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(200, client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).join().statusCode());
            assertEquals(200, client.sendAsync(request, HttpResponse.BodyHandlers.discarding(),
                    (initial, push, acceptor) -> failPush()).join().statusCode());
            assertEquals(200, client.send(explicit, HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(200, client.sendAsync(explicit, HttpResponse.BodyHandlers.discarding()).join().statusCode());

            // A rejected upgrade still lets us verify the authorization sent on the initial handshake.
            final var websocketUri = URI.create("ws://kubernetes.api/test");
            final var basicFailure = assertThrows(CompletionException.class, () -> client.newWebSocketBuilder()
                    .buildAsync(websocketUri, new WebSocket.Listener() {}).join());
            assertInstanceOf(WebSocketHandshakeException.class, basicFailure.getCause());
            final var explicitFailure = assertThrows(CompletionException.class, () -> client.newWebSocketBuilder()
                    .header("aUtHoRiZaTiOn", "Custom value")
                    .buildAsync(websocketUri, new WebSocket.Listener() {}).join());
            assertInstanceOf(WebSocketHandshakeException.class, explicitFailure.getCause());

            assertEquals(List.of(
                    List.of("Basic dXNlcjpwYTpzcw=="), List.of("Basic dXNlcjpwYTpzcw=="), List.of("Basic dXNlcjpwYTpzcw=="),
                    List.of("Custom value"), List.of("Custom value"),
                    List.of("Basic dXNlcjpwYTpzcw=="), List.of("Custom value")), received);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void bearerAndExecTakePrecedence(@TempDir final Path work) throws IOException {
        assertAuthorization(configuration(work, "yaml", "http://localhost", "    token: kube-token\n"), "Bearer kube-token");
        final var tokenFile = Files.writeString(work.resolve("token"), "file-token\n");
        assertAuthorization(configuration(work, "yaml", "http://localhost", "    tokenFile: " + tokenFile + '\n'), "Bearer file-token");
        assertAuthorization(configuration(work, "yaml", "http://localhost", "").setToken("configured-token"), "Bearer configured-token");
        assertAuthorization(configuration(work, "yaml", "http://localhost", "").setTokenEvaluator(() -> "evaluated-token"), "Bearer evaluated-token");
        final var exec = configuration(work, "json", "http://localhost", "");
        Files.writeString(exec.getKubeconfig(), Files.readString(exec.getKubeconfig())
                .replace("\"password\": \"pa:ss\"", """
                        "password": "pa:ss",
                        "exec": {
                          "apiVersion": "client.authentication.k8s.io/v1",
                          "command": "echo",
                          "args": ["{\\"kind\\":\\"ExecCredential\\",\\"status\\":{\\"token\\":\\"exec-token\\"}}"]
                        }
                        """));
        assertAuthorization(exec, "Bearer exec-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"username: user", "password: pass"})
    void incompleteCredentialsDoNotEnableBasic(final String credential, @TempDir final Path work) throws IOException {
        final var config = configuration(work, "yaml", "http://localhost", "");
        Files.writeString(config.getKubeconfig(), Files.readString(config.getKubeconfig())
                .replace("username: user\n    password: pa:ss", credential));
        try (final var client = new KubernetesClient(config.setToken("none"))) {
            assertEquals(List.of(), client.prepare(HttpRequest.newBuilder(URI.create("http://kubernetes.api/test")).build())
                    .headers().allValues("Authorization"));
        }
    }

    private void failPush() {
        throw new AssertionError("Unexpected server push");
    }

    private void assertAuthorization(final KubernetesClientConfiguration configuration, final String expected) {
        try (final var client = new KubernetesClient(configuration)) {
            final var request = HttpRequest.newBuilder(URI.create("http://kubernetes.api/test")).build();
            assertEquals(List.of(expected), client.prepare(request).headers().allValues("Authorization"));
        }
    }

    private KubernetesClientConfiguration configuration(final Path work, final String format, final String server,
                                                        final String extraUser) throws IOException {
        final var kubeconfig = work.resolve("config." + format);
        Files.writeString(kubeconfig, "json".equals(format) ? """
                {
                  "current-context": "test",
                  "contexts": [{"name": "test", "context": {"cluster": "local", "user": "basic"}}],
                  "clusters": [{"name": "local", "cluster": {"server": "%s"}}],
                  "users": [{"name": "other", "user": {"username": "wrong", "password": "wrong"}},
                            {"name": "basic", "user": {"username": "user", "password": "pa:ss"}}]
                }
                """.formatted(server) : """
                current-context: test
                contexts:
                - name: test
                  context:
                    cluster: local
                    user: basic
                clusters:
                - name: local
                  cluster:
                    server: %s
                users:
                - name: other
                  user:
                    username: wrong
                    password: wrong
                - name: basic
                  user:
                    username: user
                    password: pa:ss
                %s""".formatted(server, extraUser));
        return new KubernetesClientConfiguration().setKubeconfig(kubeconfig);
    }
}
