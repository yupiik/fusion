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

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.configuration.impl.MapConfigSource;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates the merged observability wiring end to end: the http-server FusionModule registers the
 * {@code Health}/{@code Metrics} monitoring endpoints and the registries, monitoring is enabled from the
 * configuration (auto-collecting {@link HealthCheck} beans) and the endpoints are only exposed on the monitoring server port.
 */
class ObservabilityServerTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void metricsAndHealth() throws IOException, InterruptedException {
        final var sample = new SampleCheck();
        try (final var container = ConfiguringContainer.of()
                .register(
                        new ProvidedInstanceBean<>(ApplicationScoped.class, ConfigurationSource.class,
                                () -> new MapConfigSource(Map.of(
                                        "fusion.http-server.monitoring.enabled", "true",
                                        "fusion.http-server.monitoring.port", "0"))),
                        new ProvidedInstanceBean<>(ApplicationScoped.class, HealthCheck.class, () -> sample))
                .start();
             final var webServer = container.lookup(WebServer.class)) {

            final var httpServer = webServer.instance();
            final var monitoringConfiguration = httpServer.configuration()
                    .unwrap(TomcatWebServerConfiguration.class)
                    .getMonitoringServerConfiguration();
            final var monitoringPort = monitoringConfiguration.getPort();

            // exactly the auto-registered Health and Metrics monitoring endpoints are deployed
            assertEquals(2, monitoringConfiguration.getEndpoints().size());

            try (final var lookup = container.lookup(MetricsRegistry.class)) {
                lookup.instance().registerReadOnlyGauge("wired_gauge", "value", () -> 42);
            }

            final var metrics = get("http://localhost:" + monitoringPort + "/metrics");
            assertEquals(200, metrics.statusCode(), metrics::body);
            assertEquals("""
                            # TYPE wired_gauge gauge
                            # UNIT wired_gauge value
                            wired_gauge 42
                            # EOF""",
                    metrics.body());

            final var health = get("http://localhost:" + monitoringPort + "/health");
            assertEquals(200, health.statusCode(), health::body);
            assertEquals("test-check,OK,\"worked\"", health.body());

            // not exposed on the main port
            final var mainPort = httpServer.configuration().port();
            assertEquals(404, get("http://localhost:" + mainPort + "/health").statusCode());
            assertEquals(404, get("http://localhost:" + mainPort + "/metrics").statusCode());
        }
    }

    private HttpResponse<String> get(final String uri) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder().GET().uri(URI.create(uri)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}