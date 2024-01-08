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
package io.yupiik.fusion.kubernetes.client.internal;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class LightYamlParserTest {
    private final LightYamlParser parser = new LightYamlParser();

    @TestFactory
    Stream<DynamicTest> parse() {
        return Stream.of(
                dynamicTest("cert_auth", () -> assertParse(
                        map(
                                "apiVersion", "v1",
                                "clusters", List.of(map(
                                        "cluster", map(
                                                "certificate-authority-data", "aaaaaaaaaaaa",
                                                "server", "https://1.2.3.4:8443"),
                                        "name", "k0s")),
                                "contexts", List.of(map(
                                        "context", map(
                                                "cluster", "k0s",
                                                "namespace", "dev",
                                                "user", "user"),
                                        "name", "k0s")),
                                "current-context", "k0s",
                                "kind", "Config",
                                "preferences", map(),
                                "users", List.of(map(
                                        "name", "user",
                                        "user", map(
                                                "client-certificate-data", "bbbbbbb",
                                                "client-key-data", "ccccccccccc")))), """
                                apiVersion: v1
                                clusters:
                                - cluster:
                                    certificate-authority-data: aaaaaaaaaaaa
                                    server: https://1.2.3.4:8443
                                  name: k0s
                                contexts:
                                - context:
                                    cluster: k0s
                                    namespace: dev
                                    user: user
                                  name: k0s
                                current-context: "k0s"
                                kind: Config
                                preferences: {}
                                users:
                                - name: user
                                  user:
                                    client-certificate-data: bbbbbbb
                                    client-key-data: ccccccccccc""")),
                dynamicTest("token_auth", () -> assertParse(
                        map(
                                "apiVersion", "v1",
                                "kind", "Config",
                                "clusters", List.of(map(
                                        "cluster", map(
                                                "certificate-authority-data", "<CA_DATA>",
                                                "server", "<SERVER>"),
                                        "name", "my-cluster")),
                                "contexts", List.of(map(
                                        "name", "default-context",
                                        "context", map(
                                                "cluster", "my-cluster",
                                                "user", "default-user"))),
                                "current-context", "default-context",
                                "users", List.of(map(
                                        "name", "my-service",
                                        "user", map("token", "<TOKEN>")))), """
                                apiVersion: v1
                                kind: Config
                                clusters:
                                - cluster:
                                    certificate-authority-data: <CA_DATA>
                                    server: <SERVER>
                                  name: my-cluster
                                contexts:
                                - context:
                                  name: default-context
                                  context:
                                    cluster: my-cluster
                                    user: default-user
                                current-context: default-context
                                users:
                                - name: my-service
                                  user:
                                    token: <TOKEN>""")),
                dynamicTest("complex", () -> assertParse(
                        map(
                                "current-context", "federal-context",
                                "apiVersion", "v1",
                                "clusters", List.of(
                                        map(
                                                "cluster", map(
                                                        "api-version", "v1",
                                                        "server", "http://cow.org:8080"),
                                                "name", "cow-cluster"),
                                        map(
                                                "cluster", map(
                                                        "certificate-authority", "path/to/my/cafile",
                                                        "server", "https://horse.org:4443"),
                                                "name", "horse-cluster"),
                                        map(
                                                "cluster", map(
                                                        "insecure-skip-tls-verify", "true",
                                                        "server", "https://pig.org:443"),
                                                "name", "pig-cluster")),
                                "contexts", List.of(
                                        map(
                                                "context", map(
                                                        "cluster", "horse-cluster",
                                                        "namespace", "chisel-ns",
                                                        "user", "green-user"),
                                                "name", "federal-context"),
                                        map(
                                                "context", map(
                                                        "cluster", "pig-cluster",
                                                        "namespace", "saw-ns",
                                                        "user", "black-user"),
                                                "name", "queen-anne-context")),
                                "kind", "Config",
                                "preferences", map("colors", "true"),
                                "users", List.of(
                                        map(
                                                "name", "blue-user",
                                                "user", map("token", "blue-token")),
                                        map(
                                                "name", "green-user",
                                                "user", map(
                                                        "client-certificate", "path/to/my/client/cert",
                                                        "client-key", "path/to/my/client/key")))),
                        """
                                current-context: federal-context
                                apiVersion: v1
                                clusters:
                                - cluster:
                                    api-version: v1
                                    server: http://cow.org:8080
                                  name: cow-cluster
                                - cluster:
                                    certificate-authority: path/to/my/cafile
                                    server: https://horse.org:4443
                                  name: horse-cluster
                                - cluster:
                                    insecure-skip-tls-verify: true
                                    server: https://pig.org:443
                                  name: pig-cluster
                                contexts:
                                - context:
                                    cluster: horse-cluster
                                    namespace: chisel-ns
                                    user: green-user
                                  name: federal-context
                                - context:
                                    cluster: pig-cluster
                                    namespace: saw-ns
                                    user: black-user
                                  name: queen-anne-context
                                kind: Config
                                preferences:
                                  colors: true
                                users:
                                - name: blue-user
                                  user:
                                    token: blue-token
                                - name: green-user
                                  user:
                                    client-certificate: path/to/my/client/cert
                                    client-key: path/to/my/client/key
                                                                
                                """))
        );
    }

    private void assertParse(final Map<String, Object> expected, final String kubeconfig) throws IOException {
        try (final var reader = new BufferedReader(new StringReader(kubeconfig))) {
            final var parsed = parser.parse(reader);
            assertEquals(expected, parsed);
        }
    }

    private Map<String, Object> map(final Object... data) {
        final var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < data.length / 2; i++) {
            final var baseIdx = i * 2;
            map.put(data[baseIdx].toString(), data[baseIdx + 1]);
        }
        return map;
    }
}
