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


package io.yupiik.fusion.http.server.impl.http;

import io.yupiik.fusion.http.server.impl.health.Health;
import io.yupiik.fusion.http.server.impl.health.HealthCheck;
import io.yupiik.fusion.http.server.impl.health.HealthRegistry;
import io.yupiik.fusion.http.server.impl.metrics.Metrics;
import io.yupiik.fusion.http.server.impl.tomcat.MonitoringServerConfiguration;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import io.yupiik.fusion.observability.metrics.MetricsRegistry;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Runtime tests for observability endpoints exposed by the HTTP server.
 *
 * <p>
 * These tests intentionally avoid Fusion/CDI and the build processor.
 * The http-server module wires monitoring endpoints explicitly at runtime,
 * so the tests reflect the actual production behavior.
 * </p>
 */
class ObservabilityServerTest {

    // Plain JDK client to exercise the server as a real consumer would
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void metrics() throws Exception {
        // Metrics registry is created explicitly:
        // http-server does not rely on the processor / CDI at runtime
        final var registry = new MetricsRegistry();
        registry.registerReadOnlyGauge("my_gauge", "value", () -> 100);

        // Monitoring endpoints are wired manually to reflect real runtime usage
        final var monitoring = new MonitoringServerConfiguration()
                .setPort(0) // let the OS choose a free port
                .setEndpoints(List.of(new Metrics(registry)));

        final var configuration = new TomcatWebServerConfiguration();
        configuration.setPort(0); // main HTTP server port
        configuration.setMonitoringServerConfiguration(monitoring);

        // Start a real Tomcat instance to validate monitoring behavior end-to-end
        try (var server = new TomcatWebServer(configuration)) {
            final int monitoringPort = monitoring.getPort();

            final var response = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + monitoringPort + "/metrics"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("my_gauge"));
        } finally {
            // Cleanup to avoid leaking state across tests
            registry.unregisterGauge("my_gauge");
        }
    }

    @Test
    void health() throws Exception {
        // HealthCheck implemented inline to avoid relying on observability internals
        final HealthCheck okCheck = new HealthCheck() {
            @Override
            public String name() {
                return "test-check";
            }

            @Override
            public CompletableFuture<Result> check() {
                return CompletableFuture.completedFuture(
                        new Result(Status.OK, "worked"));
            }
        };

        // HealthRegistry is overridden to expose a fixed set of checks
        // (no mutation, no internal field access)
        final HealthRegistry registry = new HealthRegistry() {
            @Override
            public List<HealthCheck> healthChecks() {
                return List.of(okCheck);
            }
        };

        final var monitoring = new MonitoringServerConfiguration()
                .setPort(0)
                .setEndpoints(List.of(new Health(registry)));

        final var configuration = new TomcatWebServerConfiguration();
        configuration.setPort(0);
        configuration.setMonitoringServerConfiguration(monitoring);

        try (var server = new TomcatWebServer(configuration)) {
            final int monitoringPort = monitoring.getPort();

            final var response = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:" + monitoringPort + "/health"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("test-check,OK,\"worked\"", response.body());
        }
    }
}
