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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KubeconfigProxyTest {
    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void authenticatesWithDecodedProxyCredentials(final String format, @TempDir final Path work) throws Exception {
        final var expected = "Basic " + Base64.getEncoder().encodeToString("u@ser+name:p:ss+word".getBytes(UTF_8));
        final var received = new CopyOnWriteArrayList<String>();
        final var originAuthorization = new CopyOnWriteArrayList<String>();
        final var proxy = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        proxy.createContext("/", exchange -> {
            try (exchange) {
                final var authorization = exchange.getRequestHeaders().getFirst("Proxy-Authorization");
                received.add(authorization == null ? "" : authorization);
                originAuthorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
                if (!expected.equals(authorization)) {
                    exchange.getResponseHeaders().set("Proxy-Authenticate", "Basic realm=\"test-proxy\"");
                    exchange.sendResponseHeaders(407, -1);
                } else {
                    exchange.sendResponseHeaders(200, -1);
                }
            }
        });
        proxy.start();
        try {
            final var config = configuration(work, format, "http://u%40ser+name:p%3Ass%2Bword@localhost:" + proxy.getAddress().getPort())
                    .setTokenEvaluator(() -> "api-token");
            try (final var client = new KubernetesClient(config)) {
                final var response = client.send(HttpRequest.newBuilder(URI.create("http://kubernetes.api/test"))
                        .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.discarding());
                assertEquals(200, response.statusCode());
                assertEquals(List.of("", expected), received);
                assertEquals(List.of("Bearer api-token", "Bearer api-token"), originAuthorization);
            }
        } finally {
            proxy.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void credentialsAreScopedToConfiguredProxy(final String format, @TempDir final Path work) throws IOException {
        for (final var credentials : List.of(
                new String[]{"http://u%40ser+name:p%3Ass%2Bword@localhost:3128", "u@ser+name", "p:ss+word", "localhost", "3128"},
                new String[]{"http://user@localhost", "user", "", "localhost", "80"},
                new String[]{"http://user:@localhost:3128", "user", "", "localhost", "3128"},
                new String[]{"http://user:pass@[::1]:3128", "user", "pass", "::1", "3128"})) {
            try (final var client = new KubernetesClient(configuration(work, format, credentials[0]))) {
                final var authenticator = client.authenticator().orElseThrow();
                final var host = credentials[3];
                final var port = Integer.parseInt(credentials[4]);
                final var password = authenticator.requestPasswordAuthenticationInstance(host.toUpperCase(Locale.ROOT), null, port,
                        "http", "test", "basic", null, Authenticator.RequestorType.PROXY);
                assertEquals(credentials[1], password.getUserName());
                assertEquals(credentials[2], new String(password.getPassword()));
                assertNull(authenticator.requestPasswordAuthenticationInstance(host, null, port,
                        "http", "test", "basic", null, Authenticator.RequestorType.SERVER));
                assertNull(authenticator.requestPasswordAuthenticationInstance("other.invalid", null, port,
                        "http", "test", "basic", null, Authenticator.RequestorType.PROXY));
                assertNull(authenticator.requestPasswordAuthenticationInstance(host, null, port + 1,
                        "http", "test", "basic", null, Authenticator.RequestorType.PROXY));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void sendsThroughSelectedClusterProxy(final String format, @TempDir final Path work) throws Exception {
        final var proxy = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        proxy.createContext("/", exchange -> {
            try (exchange) {
                final var body = exchange.getRequestURI().toString().getBytes(UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        proxy.start();
        try {
            final var config = configuration(work, format, "http://localhost:" + proxy.getAddress().getPort());
            try (final var client = new KubernetesClient(config)) {
                final var response = client.send(HttpRequest.newBuilder(URI.create("http://kubernetes.api/test?watch=true"))
                        .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertEquals("http://cluster.invalid/test?watch=true", response.body());
            }
        } finally {
            proxy.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void supportsHttpProxyForHttpAndHttpsTargets(final String format, @TempDir final Path work) throws IOException {
        for (final var url : List.of("http://localhost", "HTTP://localhost:8080/", "http://[::1]:8080")) {
            try (final var client = new KubernetesClient(configuration(work, format, url))) {
                for (final var scheme : List.of("http", "https")) {
                    final var selected = client.proxy().orElseThrow().select(URI.create(scheme + "://cluster.invalid/test"));
                    assertEquals(1, selected.size());
                    assertEquals(Proxy.Type.HTTP, selected.get(0).type());
                    assertEquals(url.equals("http://localhost") ? 80 : 8080,
                            ((InetSocketAddress) selected.get(0).address()).getPort());
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void customizerOverridesKubeconfigProxy(final String format, @TempDir final Path work) throws IOException {
        final var override = ProxySelector.of(InetSocketAddress.createUnresolved("override.invalid", 1234));
        final var authenticator = new Authenticator() {};
        final var config = configuration(work, format, "http://user:pass@localhost:8080")
                .setClientCustomizer(builder -> builder.proxy(override).authenticator(authenticator));
        try (final var client = new KubernetesClient(config)) {
            assertSame(override, client.proxy().orElseThrow());
            assertSame(authenticator, client.authenticator().orElseThrow());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void reusedConfigurationDoesNotRetainProxyCustomizers(final String format, @TempDir final Path work) throws IOException {
        for (final var withCustomizer : List.of(false, true)) {
            final var calls = new AtomicInteger();
            final Consumer<HttpClient.Builder> customizer = withCustomizer ? builder -> {
                calls.incrementAndGet();
                builder.connectTimeout(Duration.ofSeconds(5));
            } : null;
            final var config = configuration(work, format, null).setClientCustomizer(customizer).setMaster("http://configured");
            for (final var proxy : new String[]{"http://user:pass@localhost:3128", "http://localhost:3129", null}) {
                // Rewrite the same kubeconfig while retaining the configuration instance.
                configuration(work, format, proxy);
                try (final var client = new KubernetesClient(config)) {
                    if (proxy == null) {
                        assertTrue(client.proxy().isEmpty());
                    } else {
                        final var selected = client.proxy().orElseThrow().select(URI.create("http://cluster.invalid/test"));
                        assertEquals(URI.create(proxy).getPort(), ((InetSocketAddress) selected.get(0).address()).getPort());
                    }
                    assertEquals(proxy != null && proxy.contains("@"), client.authenticator().isPresent());
                    if (withCustomizer) {
                        assertEquals(Duration.ofSeconds(5), client.connectTimeout().orElseThrow());
                    }
                    assertSame(customizer, config.getClientCustomizer());
                    assertEquals("http://configured", config.getMaster());
                }
            }
            assertEquals(withCustomizer ? 3 : 0, calls.get());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void failedConstructionLeavesConfigurationUnchanged(final String format, @TempDir final Path work) throws IOException {
        final Consumer<HttpClient.Builder> customizer = builder -> {
            throw new IllegalStateException("customizer failure");
        };
        final var config = configuration(work, format, "http://user:pass@localhost:3128")
                .setClientCustomizer(customizer).setMaster("http://configured").setCertificates("configured-ca");
        Files.writeString(config.getKubeconfig(), Files.readString(config.getKubeconfig())
                .replace("\"server\": \"http://cluster.invalid\"", "\"server\": \"http://cluster.invalid\", \"certificate-authority-data\": \"dGVzdC1jYQ==\", \"insecure-skip-tls-verify\": true")
                .replace("server: http://cluster.invalid", "server: http://cluster.invalid\n    certificate-authority-data: dGVzdC1jYQ==\n    insecure-skip-tls-verify: true"));
        assertEquals("customizer failure", assertThrows(IllegalStateException.class, () -> new KubernetesClient(config)).getMessage());
        assertSame(customizer, config.getClientCustomizer());
        assertEquals("http://configured", config.getMaster());
        assertEquals("configured-ca", config.getCertificates());
        assertFalse(config.isSkipTls());
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void suppliedClientIsNotChanged(final String format, @TempDir final Path work) throws IOException {
        final var override = ProxySelector.of(InetSocketAddress.createUnresolved("override.invalid", 1234));
        final var delegate = HttpClient.newBuilder().proxy(override).build();
        // An unused kubeconfig proxy is not validated when the caller supplies the client.
        final var config = configuration(work, format, "socks5://localhost:1080").setClient(delegate);
        try (final var client = new KubernetesClient(config)) {
            assertSame(override, client.proxy().orElseThrow());
            assertNull(config.getClientCustomizer());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void missingOrBlankProxyRetainsDefaultBehavior(final String format, @TempDir final Path work) throws IOException {
        for (final var url : new String[]{null, "", " "}) {
            try (final var client = new KubernetesClient(configuration(work, format, url))) {
                assertTrue(client.proxy().isEmpty());
                assertTrue(client.authenticator().isEmpty());
            }
        }
        try (final var client = new KubernetesClient(new KubernetesClientConfiguration()
                .setKubeconfig(null).setMaster("http://cluster.invalid").setToken("none"))) {
            assertTrue(client.proxy().isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void rejectsInvalidOrUnsupportedProxies(final String format, @TempDir final Path work) throws IOException {
        for (final var url : List.of("not a url", "localhost:8080", "http:///", "http://localhost:0",
                "http://localhost:65536", "https://localhost:8443", "socks5://localhost:1080",
                "http://user:secret%ZZ@localhost:8080", "http://user:secret@localhost:65536",
                "http://localhost/path", "http://localhost?secret", "http://localhost/#secret")) {
            final var config = configuration(work, format, url);
            final var error = assertThrows(IllegalArgumentException.class, () -> new KubernetesClient(config), url);
            assertTrue(error.getMessage().contains("kubeconfig proxy-url"));
            assertFalse(error.getMessage().contains("secret"));
            assertNull(config.getClientCustomizer());
        }
    }

    private KubernetesClientConfiguration configuration(final Path work, final String format, final String proxy) throws IOException {
        final var path = work.resolve("proxy." + format);
        final var extra = proxy == null ? "" : "json".equals(format) ?
                ", \"proxy-url\": \"" + proxy + '"' : "    proxy-url: \"" + proxy + "\"\n";
        // The first cluster must not be used: the selected context references the second one.
        Files.writeString(path, "json".equals(format) ? """
                {
                  "current-context": "selected",
                  "contexts": [{"name": "selected", "context": {"cluster": "local"}}],
                  "clusters": [
                    {"name": "unselected", "cluster": {"server": "http://unused.invalid", "proxy-url": "socks5://unused.invalid:1080"}},
                    {"name": "local", "cluster": {"server": "http://cluster.invalid"%s}}
                  ]
                }
                """.formatted(extra) : """
                current-context: selected
                contexts:
                - name: selected
                  context:
                    cluster: local
                clusters:
                - name: unselected
                  cluster:
                    server: http://unused.invalid
                    proxy-url: socks5://unused.invalid:1080
                - name: local
                  cluster:
                    server: http://cluster.invalid
                %s""".formatted(extra));
        return new KubernetesClientConfiguration().setKubeconfig(path).setToken("none");
    }
}
