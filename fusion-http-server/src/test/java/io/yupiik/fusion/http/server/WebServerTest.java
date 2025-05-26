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

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.MonitoringServerConfiguration;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import io.yupiik.fusion.http.server.spi.Endpoint;
import io.yupiik.fusion.http.server.spi.MonitoringEndpoint;
import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.lang.Thread.sleep;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.util.concurrent.CompletableFuture.completedStage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebServerTest {
    @Test
    void longStreamingResponse() throws IOException, InterruptedException {
        final int numberOfA = (int) (8192 * 2.5); // if too big test will be too long but we want more than one chunk/onNext(ByteBuffer)
        final var configuration = WebServer.Configuration.of().port(0);
        final var tomcat = configuration.unwrap(TomcatWebServerConfiguration.class);
        tomcat.setEndpoints(List.of(new Endpoint() {
            @Override
            public boolean matches(final Request request) {
                return "GET".equalsIgnoreCase(request.method()) && "/test".equalsIgnoreCase(request.path());
            }

            @Override
            public CompletionStage<Response> handle(final Request request) {
                return completedStage(Response.of()
                        .status(212)
                        .body(HttpRequest.BodyPublishers.ofString("a".repeat(numberOfA), StandardCharsets.UTF_8))
                        .build());
            }
        }));
        final var http = HttpClient.newHttpClient();
        try (final var server = WebServer.of(configuration)) {
            final var res = http.send(HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://" + configuration.host() + ":" + configuration.port() + "/test"))
                            .build(),
                    ofString());
            assertEquals(212, res.statusCode(), res::body);

            final var repeat = "a".repeat(numberOfA);
            assertEquals(repeat.length(), res.body().length()); // easier error message but useless functionally
            assertEquals(repeat, res.body());
        }
    }

    @Test
    void enableMonitoring() throws IOException, InterruptedException {
        final var configuration = WebServer.Configuration.of().port(0);
        final var tomcat = configuration.unwrap(TomcatWebServerConfiguration.class);
        tomcat.setMonitoringServerConfiguration(new MonitoringServerConfiguration()
                .setPort(0)
                .setEndpoints(List.of(new MonitoringEndpoint() {
                    @Override
                    public CompletionStage<Response> unsafeHandle(final Request request) {
                        return completedStage(Response.of().body("monitoring: true").build());
                    }

                    @Override
                    public boolean matches(final Request request) {
                        return "GET".equalsIgnoreCase(request.method()) && "/test".equalsIgnoreCase(request.path());
                    }
                })));
        try (final var server = WebServer.of(configuration)) {
            final var port = tomcat.getMonitoringServerConfiguration().getPort();
            assertNotEquals(0, port);
            assertNotEquals(8081, port);

            final var get = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:" + port + "/test"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, get.statusCode(), get::body);
            assertEquals("monitoring: true", get.body());
        }
    }

    @Test
    void runEmpty() {
        try (final var server = WebServer.of(WebServer.Configuration.of().port(0))) {
            assertNotEquals(0, server.configuration().port());
        }
    }

    @Test
    void stopOnFailure(final TestInfo testInfo) throws InterruptedException, ExecutionException {
        final var tg = new ThreadGroup(testInfo.getTestClass().orElseThrow().getName() + "#" + testInfo.getTestMethod().orElseThrow().getName());
        final var result = new CompletableFuture<Void>();
        final var thread = new Thread(tg, () -> {
            try {
                final var configuration = WebServer.Configuration.of().port(0);
                configuration.unwrap(TomcatWebServerConfiguration.class).setInitializers(List.of((set, servletContext) -> {
                    final var servlet = servletContext.addServlet("test", new HttpServlet() {
                        @Override
                        public void init() {
                            throw new IllegalArgumentException("intended error for tests");
                        }
                    });
                    servlet.setLoadOnStartup(1);
                    servlet.addMapping("/test");
                }));
                final var threadsBefore = listThreads(tg);
                assertThrows(IllegalStateException.class, () -> WebServer.of(configuration));

                final var maxRetries = 3;
                for (int i = 0; i < maxRetries; i++) { // support a small retry for the CI
                    try {
                        final var current = Set.of(listThreads(tg));
                        assertEquals(Set.of(threadsBefore), current);
                        break;
                    } catch (final AssertionError ae) {
                        if (i + 1 == maxRetries) {
                            throw ae;
                        }
                        try {
                            sleep(100);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    }
                }
                result.complete(null);
            } catch (final RuntimeException | Error e) {
                result.completeExceptionally(e);
            }
        }, tg.getName());
        thread.start();
        thread.join();
        result.get();
    }

    private Thread[] listThreads(final ThreadGroup group) {
        final var threads = new Thread[Thread.activeCount()];
        Thread.enumerate(threads);
        return Stream.of(threads).filter(i -> i.getThreadGroup() == group).toArray(Thread[]::new);
    }
}
