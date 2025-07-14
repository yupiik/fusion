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
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.testing.http.CapturingHttpServer;
import io.yupiik.kubernetes.operator.base.test.sample.SampleLoggingCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static io.yupiik.fusion.testing.assertion.Asserts.waitUntil;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleOperatorTest {
    @Test
    void handleEvents() {
        final var controller = new MockController();
        try (final var logs = new SampleLoggingCapture(2, m -> m.startsWith("[") && m.contains("test1"));
             final var kubernetes = new CapturingHttpServer(new CapturingHttpServer.CapturingHttpServerConfiguration()
                     .handler(ex -> {
                         boolean close = true;
                         try {
                             if ("GET".equals(ex.getRequestMethod())) {
                                 if (ex.getRequestURI().getPath().equals("/apis/fusion.yupiik.io/v1/namespaces/default/samples")) {
                                     final var query = ex.getRequestURI().getQuery();
                                     final var watch = query != null && query.contains("watch=true");
                                     close = !watch;
                                     final var response = (watch ?
                                             "" /* length=0 -> chunking as we want */ :
                                             """
                                                     {
                                                       "items":[]
                                                     }""").getBytes(StandardCharsets.UTF_8);
                                     ex.sendResponseHeaders(200, response.length);
                                     ex.getResponseBody().write(response);
                                     if (watch) {
                                         controller.setWriter(ex);
                                     } else {
                                         ex.close();
                                     }
                                     return false;
                                 }
                             }
                             try (ex) {
                                 ex.sendResponseHeaders(404, 0);
                             }
                         } catch (final IOException e) {
                             throw new IllegalStateException(e);
                         } finally {
                             if (close) {
                                 ex.close();
                             }
                         }
                         return false;
                     }));
             final var operator = ConfiguringContainer.of()
                     .register(new ProvidedInstanceBean<>(ApplicationScoped.class, ConfigurationSource.class, () -> configurationSource(kubernetes, null)))
                     .start()) {
            controller.sendEvent("""
                    {
                     "type":"ADDED",
                     "object": {
                       "kind": "Sample",
                       "apiVersion": "fusion.yupiik.io/v1",
                       "metadata": {
                         "name": "test1",
                         "namespace": "default"
                       },
                       "spec": {
                         "message": "hello"
                       }
                     }
                    }""");
            controller.sendEvent("""
                    {
                     "type":"DELETED",
                     "object": {
                       "kind": "Sample",
                       "apiVersion": "fusion.yupiik.io/v1",
                       "metadata": {
                         "name": "test1",
                         "namespace": "default"
                       },
                       "spec": {
                         "message": "hello"
                       }
                     }
                    }""");
            try {
                assertEquals(
                        List.of(
                                "[ADD] SampleResource[metadata=Metadata[uid=null, resourceVersion=null, name=test1, namespace=default, labels=null, annotations=null], spec=Spec[message=hello]]",
                                "[DELETE] SampleResource[metadata=Metadata[uid=null, resourceVersion=null, name=test1, namespace=default, labels=null, annotations=null], spec=Spec[message=hello]]"
                        ),
                        logs.all());
            } finally {
                controller.close();
            }
        }
    }

    @Test
    @Timeout(value = 1, unit = MINUTES)
    void persistAndRestart(@TempDir final Path storage) throws IOException {
        { // start without state
            final var end = new CountDownLatch(1);
            try (
                    final var kubernetes = new CapturingHttpServer(spec -> {
                        spec.map(
                                "GET", "/apis/fusion.yupiik.io/v1/namespaces/default/samples?limit=1",
                                rs -> rs.send(
                                        200, Map.of(),
                                        """
                                                {
                                                  "metadata": {"resourceVersion":"1234"},
                                                  "items": []
                                                }"""));
                        spec.map(
                                "GET", "/apis/fusion.yupiik.io/v1/namespaces/default/samples?watch=true",
                                rs -> rs.send(
                                        200, Map.of(),
                                        new CapturingHttpServer.ResponseBodyHandler() {
                                            @Override
                                            public HttpRequest.BodyPublisher publisher() {
                                                return HttpRequest.BodyPublishers.concat(
                                                        HttpRequest.BodyPublishers.ofString("""
                                                                {
                                                                 "type":"ADDED",
                                                                 "object": {
                                                                   "kind": "Sample",
                                                                   "apiVersion": "fusion.yupiik.io/v1",
                                                                   "metadata": {
                                                                     "name": "test2",
                                                                     "namespace": "default",
                                                                     "resourceVersion": "12346"
                                                                   },
                                                                   "spec": {
                                                                     "message": "hello"
                                                                   }
                                                                 }
                                                                }""".replace("\n", "") + "\n"),
                                                        // hang to avoid an operator loop
                                                        HttpRequest.BodyPublishers.ofInputStream(() -> new InputStream() {
                                                            @Override
                                                            public int read() {
                                                                try {
                                                                    end.await();
                                                                } catch (final InterruptedException e) {
                                                                    Thread.currentThread().interrupt();
                                                                }
                                                                return -1;
                                                            }
                                                        }));
                                            }
                                        }));
                    });
                    final var operator = ConfiguringContainer.of()
                            .register(new ProvidedInstanceBean<>(ApplicationScoped.class, ConfigurationSource.class, () -> configurationSource(kubernetes, storage)))
                            .start()) {
                final var tracker = storage.resolve("samples");
                try {
                    waitUntil(() -> Files.exists(tracker));
                    assertEquals("{\"resourceVersion\":\"12346\"}", Files.readString(tracker));
                } finally {
                    end.countDown();
                }
            }
        }
        { // restart with a state
            final var end = new CountDownLatch(1);
            try (
                    final var logs = new SampleLoggingCapture(1, m -> m.startsWith("[") && m.contains("test2"));
                    final var kubernetes = new CapturingHttpServer(spec -> {
                        spec.map(
                                "GET", "/apis/fusion.yupiik.io/v1/namespaces/default/samples?limit=1&resourceVersion=12346",
                                rs -> rs.send(
                                        200, Map.of(),
                                        """
                                                {
                                                  "metadata": {"resourceVersion":"123456"},
                                                  "items": []
                                                }"""));
                        spec.map(
                                "GET", "/apis/fusion.yupiik.io/v1/namespaces/default/samples?watch=true&resourceVersion=12346",
                                rs -> rs.send(
                                        200, Map.of(),
                                        new CapturingHttpServer.ResponseBodyHandler() {
                                            @Override
                                            public HttpRequest.BodyPublisher publisher() {
                                                return HttpRequest.BodyPublishers.concat(
                                                        // too old
                                                        HttpRequest.BodyPublishers.ofString("""
                                                                {
                                                                 "type":"ADDED",
                                                                 "object": {
                                                                   "kind": "Sample",
                                                                   "apiVersion": "fusion.yupiik.io/v1",
                                                                   "metadata": {
                                                                     "name": "test2",
                                                                     "namespace": "default",
                                                                     "resourceVersion": "12345"
                                                                   },
                                                                   "spec": { "message": "hello" }
                                                                 }
                                                                }""".replace("\n", "") + "\n"),
                                                        // too old (limit)
                                                        HttpRequest.BodyPublishers.ofString("""
                                                                {
                                                                 "type":"ADDED",
                                                                 "object": {
                                                                   "kind": "Sample",
                                                                   "apiVersion": "fusion.yupiik.io/v1",
                                                                   "metadata": {
                                                                     "name": "test2",
                                                                     "namespace": "default",
                                                                     "resourceVersion": "12346"
                                                                   },
                                                                   "spec": { "message": "hello" }
                                                                 }
                                                                }""".replace("\n", "") + "\n"),
                                                        // recent
                                                        HttpRequest.BodyPublishers.ofString("""
                                                                {
                                                                 "type":"ADDED",
                                                                 "object": {
                                                                   "kind": "Sample",
                                                                   "apiVersion": "fusion.yupiik.io/v1",
                                                                   "metadata": {
                                                                     "name": "test2",
                                                                     "namespace": "default",
                                                                     "resourceVersion": "12347"
                                                                   },
                                                                   "spec": { "message": "hello" }
                                                                 }
                                                                }""".replace("\n", "") + "\n"),
                                                        // hang to avoid an operator loop
                                                        HttpRequest.BodyPublishers.ofInputStream(() -> new InputStream() {
                                                            @Override
                                                            public int read() {
                                                                try {
                                                                    end.await();
                                                                } catch (final InterruptedException e) {
                                                                    Thread.currentThread().interrupt();
                                                                }
                                                                return -1;
                                                            }
                                                        }));
                                            }
                                        }));
                    }); final var operator = ConfiguringContainer.of()
                    .register(new ProvidedInstanceBean<>(ApplicationScoped.class, ConfigurationSource.class, () -> configurationSource(kubernetes, storage)))
                    .start()) {
                try {
                    final var traces = logs.all();
                    assertEquals(List.of("[ADD] SampleResource[metadata=Metadata[uid=null, resourceVersion=12347, name=test2, namespace=default, labels=null, annotations=null], spec=Spec[message=hello]]"), traces);
                } finally {
                    end.countDown();
                }
            }
        }
    }

    private ConfigurationSource configurationSource(final CapturingHttpServer kubernetes, final Path storage) {
        return key -> switch (key) {
            case "operator.kubernetes.tls-skip" -> "true";
            case "operator.kubernetes.token" -> "target/____missing____";
            case "operator.kubernetes.master" -> kubernetes.base();
            case "operator.use-bookmarks" -> "false"; // to force an auto storage
            case "operator.storage" -> storage == null ? null : storage.toString();
            case "operator.probe-port" -> "-1";
            default -> null;
        };
    }

    private static class MockController implements AutoCloseable {
        public HttpExchange writer;
        private final CountDownLatch latch = new CountDownLatch(1);

        private void setWriter(final HttpExchange writer) {
            this.writer = writer;
            this.latch.countDown();
        }

        private void sendEvent(final String event) {
            try {
                latch.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                final var responseBody = writer.getResponseBody();
                responseBody.write((event.replace("\n", "") + '\n').getBytes(StandardCharsets.UTF_8));
                responseBody.flush();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
