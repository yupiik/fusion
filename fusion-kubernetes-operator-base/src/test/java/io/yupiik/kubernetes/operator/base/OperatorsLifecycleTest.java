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
package io.yupiik.kubernetes.operator.base;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.kubernetes.operator.base.impl.OperatorConfiguration;
import io.yupiik.kubernetes.operator.base.impl.OperatorsLifecycle;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorsLifecycleTest {
    @Test
    void programmaticConfigurationRetainsConstructorsAndValidatesRetryBounds() {
        final var legacy = new OperatorConfiguration(null, true, 1, -1, false, null);
        assertTrue(legacy.enabled());
        assertEquals(new OperatorConfiguration.Retry(100, 5_000, false), legacy.retry());
        assertFalse(new OperatorConfiguration(false, null, true, 1, -1, false, null).enabled());
        assertThrows(IllegalArgumentException.class, () -> new OperatorConfiguration.Retry(0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new OperatorConfiguration.Retry(10, 1, false));
    }

    @Test
    void permanentListFailureMakesReadinessUnsuccessfulWithoutChangingLiveness() throws Exception {
        final var attempted = new CompletableFuture<Void>();
        final var requests = new AtomicInteger();
        final var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            try (exchange) {
                exchange.sendResponseHeaders(403, -1);
                attempted.complete(null);
            }
        });
        server.start();
        final int port;
        try (final var reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        final var values = Map.of(
                "operator.kubernetes.master", "http://localhost:" + server.getAddress().getPort(),
                "operator.kubernetes.token", "target/missing-token",
                "operator.kubernetes.certificates", "target/missing-ca",
                "operator.probe-port", Integer.toString(port));
        try (final var container = configured(values::get).start();
             final var threads = container.lookup(ScheduledExecutorService.class)) {
            attempted.get(5, SECONDS);
            final var client = HttpClient.newHttpClient();
            assertEquals(503, client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ready")).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(200, client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertFalse(threads.instance().isShutdown());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void twoMetadataContainersNeverResolveRuntimeConfiguration() throws Exception {
        try (final var occupied = new ServerSocket(0)) {
            final ConfigurationSource source = key -> switch (key) {
                case "operator.enabled" -> "false"; // metadata containers opt out explicitly
                case "operator.probe-port" -> Integer.toString(occupied.getLocalPort());
                case "operator.kubernetes.master" -> ":not a URI";
                case "operator.event-thread-count" -> "not an integer";
                default -> null;
            };
            try (final var first = configured(source).start();
                 final var second = configured(source).start();
                 final var mapper = first.lookup(JsonMapper.class);
                 final var lifecycle = second.lookup(OperatorsLifecycle.class)) {
                assertEquals("{}", mapper.instance().toString(Map.of()));
                lifecycle.instance().await(); // disabled await is a no-op
                assertTrue(occupied.isBound());
            }
        }
    }

    @Test
    void explicitDisableChecksOnlyEnabled() {
        final ConfigurationSource source = key -> {
            if ("operator.enabled".equals(key)) {
                return "false";
            }
            if (key.startsWith("operator.")) {
                throw new AssertionError("Disabled lifecycle resolved " + key);
            }
            return null;
        };
        try (final var container = configured(source).start();
             final var lifecycle = container.lookup(OperatorsLifecycle.class)) {
            lifecycle.instance().await();
        }
    }

    @Test
    void enabledByDefaultStartsOneWatchAndProbeAndClosesThem() throws Exception {
        final var lists = new AtomicInteger();
        final var watches = new AtomicInteger();
        final var watch = new CompletableFuture<HttpExchange>();
        final var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getRawQuery() != null && exchange.getRequestURI().getRawQuery().contains("watch=true")) {
                watches.incrementAndGet();
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('\n');
                exchange.getResponseBody().flush();
                watch.complete(exchange); // keep watch open until runtime shutdown
            } else {
                lists.incrementAndGet();
                try (exchange) {
                    final var body = "{\"metadata\":{\"resourceVersion\":\"initial\"},\"items\":[]}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            }
        });
        server.start();
        final int port;
        try (final var reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        final var values = Map.of(
                "operator.kubernetes.master", "http://localhost:" + server.getAddress().getPort(),
                "operator.kubernetes.token", "target/missing-token",
                "operator.kubernetes.certificates", "target/missing-ca",
                "operator.probe-port", Integer.toString(port));
        final ScheduledExecutorService scheduler;
        try {
            try (final var container = configured(values::get).start();
                 final var threads = container.lookup(ScheduledExecutorService.class);
                 final var config = container.lookup(OperatorConfiguration.class)) {
                assertTrue(config.instance().enabled());
                assertEquals(new OperatorConfiguration.Retry(100, 5_000, false), config.instance().retry());
                scheduler = threads.instance();
                watch.get(5, SECONDS);
                final var response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build(),
                        HttpResponse.BodyHandlers.discarding());
                assertEquals(200, response.statusCode());
                assertEquals(200, HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ready")).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode());
                assertEquals(1, lists.get());
                assertEquals(1, watches.get());
                assertFalse(scheduler.isShutdown());
            }
            assertTrue(scheduler.isShutdown());
            try (final var rebound = new ServerSocket(port)) {
                assertTrue(rebound.isBound());
            }
            assertEquals(1, watches.get());
        } finally {
            if (watch.isDone()) {
                watch.join().close();
            }
            server.stop(0);
        }
    }

    private ConfiguringContainer configured(final ConfigurationSource source) {
        return ConfiguringContainer.of().register(new ProvidedInstanceBean<>(
                DefaultScoped.class, ConfigurationSource.class, () -> source));
    }
}
