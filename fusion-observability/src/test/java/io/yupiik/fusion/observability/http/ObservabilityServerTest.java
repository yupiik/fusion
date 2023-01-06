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
package io.yupiik.fusion.observability.http;

import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.observability.health.HealthCheck;
import io.yupiik.fusion.observability.health.HealthRegistry;
import io.yupiik.fusion.observability.http.test.SampleCheck;
import io.yupiik.fusion.observability.metrics.MetricsRegistry;
import io.yupiik.fusion.testing.Fusion;
import io.yupiik.fusion.testing.FusionSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@FusionSupport
@TestInstance(PER_CLASS)
class ObservabilityServerTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void metrics(@Fusion final ObservabilityServer server, @Fusion final WebServer webServer,
                 @Fusion final MetricsRegistry registry) throws IOException, InterruptedException {
        registry.registerReadOnlyGauge("my_gauge", "value", () -> 100);
        {
            final var response = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:" + server.getPort() + "/metrics"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("""
                            # TYPE my_gauge gauge
                            # UNIT my_gauge value
                            my_gauge 100
                            # EOF""",
                    response.body());
        }
        {
            final var response = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:" + webServer.configuration().port() + "/metrics"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }
        registry.unregisterGauge("my_gauge");
    }

    @Test
    void health(@Fusion final ObservabilityServer server, @Fusion final WebServer webServer,
                @Fusion final HealthRegistry registry,
                @Fusion final SampleCheck check) throws IOException, InterruptedException {
        {
            final var response = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:" + server.getPort() + "/health"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("test-check,OK,\"worked\"", response.body());
        }

        final var oldCheck = check.check();

        final var failure = new CompletableFuture<HealthCheck.Result>();
        failure.completeExceptionally(new IllegalStateException("oops"));
        check.setCheck(failure);
        try {
            final var response = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:" + server.getPort() + "/health"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, response.statusCode());
            assertEquals("test-check,KO,\"java.lang.IllegalStateException: oops\"", response.body());
        } finally {
            check.setCheck(oldCheck);
        }

        final var ko = new CompletableFuture<HealthCheck.Result>();
        ko.complete(new HealthCheck.Result(HealthCheck.Status.KO, "oops from test"));
        check.setCheck(ko);
        try {
            final var response = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:" + server.getPort() + "/health"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, response.statusCode());
            assertEquals("test-check,KO,\"oops from test\"", response.body());
        } finally {
            check.setCheck(oldCheck);
        }

        {
            final var response = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:" + webServer.configuration().port() + "/health"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }
    }
}
