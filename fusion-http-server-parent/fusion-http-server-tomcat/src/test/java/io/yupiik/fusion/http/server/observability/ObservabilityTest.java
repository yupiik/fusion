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
package io.yupiik.fusion.http.server.observability;

import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.observability.Health;
import io.yupiik.fusion.http.server.impl.observability.Metrics;
import io.yupiik.fusion.http.server.impl.tomcat.MonitoringServerConfiguration;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservabilityTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void metrics() throws IOException, InterruptedException {
        final var registry = new MetricsRegistry();
        registry.registerReadOnlyGauge("my_gauge", "value", () -> 100);
        final var monitoring = new MonitoringServerConfiguration()
                .setPort(0)
                .setEndpoints(List.of(new Metrics(registry)));

        try (final var server = buildServer(monitoring)) {
            final var tomcat = server.configuration().unwrap(TomcatWebServerConfiguration.class);
            final var monitoringPort = tomcat.getMonitoringServerConfiguration().getPort();
            final var response = client.send(
                    HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:" + monitoringPort + "/metrics")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response::body);
            assertEquals("""
                            # TYPE my_gauge gauge
                            # UNIT my_gauge value
                            my_gauge 100
                            # EOF""",
                    response.body());

            // not exposed on the main server port
            final var mainResponse = client.send(
                    HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:" + webConfigurationPort(server) + "/metrics")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, mainResponse.statusCode(), mainResponse::body);
        }
    }

    @Test
    void health() throws IOException, InterruptedException {
        final var registry = new HealthRegistry(List.of(new SampleCheck()));
        final var monitoring = new MonitoringServerConfiguration()
                .setPort(0)
                .setEndpoints(List.of(new Health(registry)));

        try (final var server = buildServer(monitoring)) {
            final var monitoringPort = server.configuration().unwrap(TomcatWebServerConfiguration.class)
                    .getMonitoringServerConfiguration().getPort();

            var response = client.send(
                    HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:" + monitoringPort + "/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response::body);
            assertEquals("test-check,OK,\"worked\"", response.body());

            final var oldCheck = registry.healthChecks().get(0).check();

            // failing check as an exception
            final var failure = new CompletableFuture<HealthCheck.Result>();
            failure.completeExceptionally(new IllegalStateException("oops"));
            ((SampleCheck) registry.healthChecks().get(0)).setCheck(failure);
            try {
                response = client.send(
                        HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:" + monitoringPort + "/health")).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(503, response.statusCode(), response::body);
                assertEquals("test-check,KO,\"java.lang.IllegalStateException: oops\"", response.body());
            } finally {
                ((SampleCheck) registry.healthChecks().get(0)).setCheck(oldCheck);
            }

            // failing check as a KO status
            final var ko = new CompletableFuture<HealthCheck.Result>();
            ko.complete(new HealthCheck.Result(HealthCheck.Status.KO, "oops from test"));
            ((SampleCheck) registry.healthChecks().get(0)).setCheck(ko);
            try {
                response = client.send(
                        HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:" + monitoringPort + "/health")).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(503, response.statusCode(), response::body);
                assertEquals("test-check,KO,\"oops from test\"", response.body());
            } finally {
                ((SampleCheck) registry.healthChecks().get(0)).setCheck(oldCheck);
            }

            // not exposed on the main server port
            response = client.send(
                    HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:" + webConfigurationPort(server) + "/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode(), response::body);
        }
    }

    @Test
    void healthTypeFilter() throws IOException, InterruptedException {
        final var registry = new HealthRegistry(List.of(new SampleCheck(), new ReadyCheck()));
        final var monitoring = new MonitoringServerConfiguration()
                .setPort(0)
                .setEndpoints(List.of(new Health(registry)));

        try (final var server = buildServer(monitoring)) {
            final var monitoringPort = server.configuration().unwrap(TomcatWebServerConfiguration.class)
                    .getMonitoringServerConfiguration().getPort();

            // no filter: all checks
            var response = client.send(
                    HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:" + monitoringPort + "/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response::body);
            assertEquals(
                    Set.of("test-check,OK,\"worked\"", "ready-check,OK,\"ready\""),
                    Arrays.stream(response.body().split("\n")).collect(Collectors.toSet()));

            // filter on live only
            response = client.send(
                    HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:" + monitoringPort + "/health?type=live")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response::body);
            assertEquals("test-check,OK,\"worked\"", response.body());

            // filter on ready only
            response = client.send(
                    HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:" + monitoringPort + "/health?type=ready")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response::body);
            assertEquals("ready-check,OK,\"ready\"", response.body());
        }
    }

    private int webConfigurationPort(final WebServer server) {
        return server.configuration().port();
    }

    private WebServer buildServer(final MonitoringServerConfiguration monitoring) {
        final var configuration = WebServer.Configuration.of().port(0);
        configuration.unwrap(TomcatWebServerConfiguration.class).setMonitoringServerConfiguration(monitoring);
        return WebServer.of(configuration);
    }
}