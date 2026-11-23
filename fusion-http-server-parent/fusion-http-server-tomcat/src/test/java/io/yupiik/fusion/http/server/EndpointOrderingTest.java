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
package io.yupiik.fusion.http.server;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.configuration.impl.MapConfigSource;
import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.bean.FusionServerConfigurationBean;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import io.yupiik.fusion.http.server.spi.Endpoint;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EndpointOrderingTest {
    @Test
    void containerBeanOrderIsNotRoutingPriority() throws Exception {
        final var fallback = route("fallback", 0, false);
        final var override = route("override", 2000, false);
        try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true)
                .register(new FusionServerConfigurationBean(),
                        new ProvidedInstanceBean<>(DefaultScoped.class, ConfigurationSource.class,
                                () -> new MapConfigSource(Map.of("fusion.http-server.port", "0"))),
                        new FunctionalBean<>(Endpoint.class, DefaultScoped.class, 1, 0, Map.of(),
                                (c, d) -> fallback, null, null, null, false),
                        new FunctionalBean<>(Endpoint.class, DefaultScoped.class, 2000, 0, Map.of(),
                                (c, d) -> override, null, null, null, false))
                .start();
             final var configuration = container.lookup(WebServer.Configuration.class)) {
            final var tomcat = configuration.instance().unwrap(TomcatWebServerConfiguration.class);
            assertEquals(List.of(fallback, override), tomcat.getEndpoints());
            final var client = HttpClient.newHttpClient();
            try (final var server = new TomcatWebServer(tomcat)) {
                final var response = client.send(HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + tomcat.port() + "/route")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertEquals("override", response.body());
                assertEquals(List.of(fallback, override), tomcat.getEndpoints());
            }
        }
    }

    @Test
    void priorityOverridesCatchAll() throws Exception {
        final var fallback = route("fallback", Integer.MIN_VALUE, false);
        final var exact = route("exact", Integer.MAX_VALUE, true);
        assertDispatch(List.of(fallback, exact), "exact", "fallback");
    }

    @Test
    void higherPriorityBroadRouteBeatsEarlierExactRoute() throws Exception {
        assertDispatch(List.of(route("exact", 1000, true), route("broad", 2000, false)), "broad", "broad");
    }

    @Test
    void equalPriorityUsesSuppliedOrderNotSpecificity() throws Exception {
        final var broad = route("broad", 1000, false);
        final var exact = route("exact", 1000, true);
        assertDispatch(List.of(broad, exact), "broad", "broad");
        assertDispatch(List.of(exact, broad), "exact", "broad");
    }

    @Test
    void firstMatchDoesNotEvaluateLaterMatchers() throws Exception {
        final var unreachable = Endpoint.of(request -> {
            throw new IllegalStateException("Later matcher must not run");
        }, request -> completedFuture(Response.of().body("unreachable").build()), 0);
        assertDispatch(List.of(route("first", 1000, false), unreachable), "first", "first");
    }

    private Endpoint route(final String body, final int priority, final boolean exact) {
        return Endpoint.of(request -> !exact || "/route".equals(request.path()),
                request -> completedFuture(Response.of().body(body).build()), priority);
    }

    private void assertDispatch(final List<Endpoint> supplied, final String route, final String other) throws Exception {
        final var mutable = new ArrayList<>(supplied);
        final var configuration = new TomcatWebServerConfiguration();
        configuration.port(0);
        configuration.setEndpoints(mutable);
        final var client = HttpClient.newHttpClient();
        try (final var server = new TomcatWebServer(configuration)) {
            assertEquals(supplied, mutable, "Server must not reorder caller's list");
            mutable.clear(); // dispatch owns a snapshot, not the caller's list
            for (final var path : List.of("/route", "/other")) {
                final var response = client.send(HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + configuration.port() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertEquals("/route".equals(path) ? route : other, response.body());
            }
        }
    }
}
