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

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class KubeconfigTest {
    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void reusedConfigurationDoesNotRetainKubeconfigOverrides(final String format, @TempDir final Path work) throws IOException {
        final var user = "json".equals(format) ? ", \"token\": \"kube-token\"" : "    token: kube-token\n";
        final var config = configuration(work, format, "http://loaded", "", user)
                .setMaster("http://configured").setToken("configured-token");
        try (final var client = new KubernetesClient(config)) {
            assertEquals(URI.create("http://loaded"), client.base());
            assertEquals(List.of("Bearer kube-token"), client.prepare(request()).headers().allValues("Authorization"));
        }
        assertEquals("http://configured", config.getMaster());
        assertEquals("configured-token", config.getToken());
        Files.writeString(config.getKubeconfig(), "json".equals(format) ? "{}" : "apiVersion: v1\n");
        try (final var client = new KubernetesClient(config)) {
            assertEquals(URI.create("http://configured"), client.base());
            assertEquals(List.of("Bearer configured-token"), client.prepare(request()).headers().allValues("Authorization"));
        }
        assertEquals("http://configured", config.getMaster());
        assertEquals("configured-token", config.getToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void normalizesEndpoints(final String format, @TempDir final Path work) throws IOException {
        for (final var endpoint : List.of("localhost:8080/", "localhost:443/", "localhost:4430/",
                "[::1]:443/", "https://localhost:8443/", "https://localhost/a%2Fb/")) {
            final var config = configuration(work, format, endpoint, "", "");
            final var expected = endpoint.contains("://") ? endpoint.substring(0, endpoint.length() - 1) :
                    (endpoint.endsWith(":443/") ? "https://" : "http://") + endpoint.substring(0, endpoint.length() - 1);
            try (final var client = new KubernetesClient(config)) {
                assertEquals(URI.create(expected), client.base());
                assertEquals(URI.create(expected).getScheme(), client.prepare(request()).uri().getScheme());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void missingCurrentContextUsesFirstContextNotClusterName(final String format, @TempDir final Path work) throws IOException {
        final var config = configuration(work, format, "http://localhost", "", "");
        final var content = Files.readString(config.getKubeconfig());
        Files.writeString(config.getKubeconfig(), content.replace("\"current-context\": \"selected\",", "")
                .replace("current-context: selected\n", ""));
        try (final var client = new KubernetesClient(config)) {
            assertEquals(URI.create("http://localhost"), client.base());
            assertEquals("test-namespace", client.namespace().orElseThrow());
            assertEquals(List.of(), client.prepare(request()).headers().allValues("Authorization"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void incompleteConfigurationRetainsConfiguredConnection(final String format, @TempDir final Path work) throws IOException {
        for (final var name : List.of("selected", "local")) {
            final var config = configuration(work, format, "http://localhost", "", "").setMaster("http://unrelated");
            final var content = Files.readString(config.getKubeconfig());
            Files.writeString(config.getKubeconfig(), content.replace("\"name\": \"" + name + "\"", "\"name\": \"other\"")
                    .replace("name: " + name + '\n', "name: other\n"));
            try (final var client = new KubernetesClient(config)) {
                assertEquals(URI.create("http://unrelated"), client.base());
            }
        }
        final var config = configuration(work, format, "http://localhost", "", "");
        Files.writeString(config.getKubeconfig(), Files.readString(config.getKubeconfig())
                .replace("\"cluster\": \"local\"", "\"cluster\": \"local\", \"user\": \"missing\"")
                .replace("cluster: local\n", "cluster: local\n    user: missing\n"));
        try (final var client = new KubernetesClient(config)) {
            assertEquals(URI.create("http://localhost"), client.base());
            assertEquals(List.of(), client.prepare(request()).headers().allValues("Authorization"));
        }
        for (final var content : "json".equals(format) ? List.of("{}", "{\"contexts\": []}") :
                List.of("apiVersion: v1\n", "contexts:\n")) {
            Files.writeString(config.getKubeconfig(), content);
            try (final var client = new KubernetesClient(config.setMaster("http://configured"))) {
                assertEquals(URI.create("http://configured"), client.base());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void tlsFlagsAndExplicitOverride(final String format, @TempDir final Path work) throws IOException {
        for (final var flag : List.of("absent", "true", "false", "\"true\"", "\"false\"")) {
            for (final var override : List.of(false, true)) {
                final var extra = "absent".equals(flag) ? "" : "json".equals(format) ?
                        ", \"insecure-skip-tls-verify\": " + flag : "    insecure-skip-tls-verify: " + flag + '\n';
                final var config = configuration(work, format, "https://localhost", extra, "").setSkipTls(override);
                try (final var client = new KubernetesClient(config) {
                    @Override
                    protected HttpClient createClient(final KubernetesClientConfiguration effective) {
                        assertEquals(override || flag.contains("true"), effective.isSkipTls(), flag);
                        return super.createClient(effective);
                    }
                }) {
                    assertEquals(override, config.isSkipTls(), flag);
                    assertEquals(URI.create("https://localhost"), client.base());
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    void resolvesFilesRelativeToKubeconfig(final String format, @TempDir final Path work) throws IOException {
        for (final var absolute : List.of(false, true)) {
            Files.writeString(work.resolve("ca.pem"), "test-ca");
            Files.writeString(work.resolve("key.pem"), "test-key");
            Files.writeString(work.resolve("cert.pem"), "test-cert");
            Files.writeString(work.resolve("token"), "file-token\n");
            final var prefix = absolute ? work.toString().replace('\\', '/') + '/' : "nested/../";
            final var cluster = "json".equals(format) ? ", \"certificate-authority\": \"" + prefix + "ca.pem\"" :
                    "    certificate-authority: " + prefix + "ca.pem\n";
            final var user = "json".equals(format) ? """
                    , "client-key": "%1$skey.pem", "client-certificate": "%1$scert.pem", "tokenFile": "%1$stoken"
                    """.formatted(prefix) : """
                        client-key: %1$skey.pem
                        client-certificate: %1$scert.pem
                        tokenFile: %1$stoken
                    """.formatted(prefix);
            final var config = configuration(work, format, "http://localhost", cluster, user)
                    .setMaster("http://configured")
                    .setCertificates("configured-ca")
                    .setPrivateKey("configured-key")
                    .setPrivateKeyCertificate("configured-cert")
                    .setToken("configured-token");
            try (final var client = new KubernetesClient(config) {
                @Override
                protected HttpClient createClient(final KubernetesClientConfiguration effective) {
                    assertNotSame(config, effective);
                    assertEquals("test-ca", effective.getCertificates());
                    assertEquals("test-key", effective.getPrivateKey());
                    assertEquals("test-cert", effective.getPrivateKeyCertificate());
                    assertEquals(work.resolve("token").toString().replace('\\', '/'), effective.getToken().replace('\\', '/'));
                    return super.createClient(effective);
                }
            }) {
                assertEquals(URI.create("http://localhost"), client.base());
                assertEquals(List.of("Bearer file-token"), client.prepare(request()).headers().allValues("Authorization"));
            }
            assertEquals("http://configured", config.getMaster());
            assertEquals("configured-ca", config.getCertificates());
            assertEquals("configured-key", config.getPrivateKey());
            assertEquals("configured-cert", config.getPrivateKeyCertificate());
            assertEquals("configured-token", config.getToken());
            // A new client must see the updated file rather than a token copied into the kubeconfig.
            Files.writeString(work.resolve("token"), "updated-token\n");
            try (final var client = new KubernetesClient(config)) {
                assertEquals(List.of("Bearer updated-token"), client.prepare(request()).headers().allValues("Authorization"));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"yaml", "json"})
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void execWithoutApiVersion(final String format, @TempDir final Path work) throws IOException {
        final var script = Files.writeString(work.resolve("credential.sh"), """
                printf '%s' '{"kind":"ExecCredential","status":{"token":"exec-token"}}'
                """);
        final var user = "json".equals(format) ? """
                , "exec": {"command": "sh", "args": ["%s"]}
                """.formatted(script) : """
                    exec:
                      command: sh
                      args:
                      - %s
                """.formatted(script);
        final var config = configuration(work, format, "http://localhost", "", user).setToken(null);
        try (final var client = new KubernetesClient(config)) {
            assertEquals(List.of("Bearer exec-token"), client.prepare(request()).headers().allValues("Authorization"));
        }
        assertNull(config.getTokenEvaluator());
        assertNull(config.getToken());
    }

    private HttpRequest request() {
        return HttpRequest.newBuilder(URI.create("http://kubernetes.api/test")).build();
    }

    private KubernetesClientConfiguration configuration(final Path work, final String format, final String server,
                                                        final String clusterExtra, final String userExtra) throws IOException {
        final var path = work.resolve("config." + format);
        Files.writeString(path, "json".equals(format) ? """
                {
                  "current-context": "selected",
                  "contexts": [{"name": "selected", "context": {"cluster": "local", "namespace": "test-namespace"%s}}],
                  "clusters": [{"name": "local", "cluster": {"server": "%s"%s}}],
                  "users": [{"name": "credentials", "user": {"username": "unused"%s}}]
                }
                """.formatted(userExtra.isEmpty() ? "" : ", \"user\": \"credentials\"", server, clusterExtra, userExtra) : """
                current-context: selected
                contexts:
                - name: selected
                  context:
                    cluster: local
                    namespace: test-namespace
                %sclusters:
                - name: local
                  cluster:
                    server: %s
                %susers:
                - name: credentials
                  user:
                    username: unused
                %s""".formatted(userExtra.isEmpty() ? "" : "    user: credentials\n", server, clusterExtra, userExtra));
        return new KubernetesClientConfiguration().setKubeconfig(path).setToken("none");
    }
}
