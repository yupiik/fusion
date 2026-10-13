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
package io.yupiik.fusion.http.server.impl.bean;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.ByteBuffers;
import io.yupiik.fusion.http.server.spi.Endpoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

import static java.net.http.HttpResponse.BodyHandlers.discarding;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class FusionServerBeanTest {
    @Test
    void run() throws IOException, InterruptedException {
        try (final var container = ConfiguringContainer.of()
                .register(new GETEndpointBean(), new POSTEndpointBean())
                .start();
             final var server = container.lookup(WebServer.class)) {
            final var http = HttpClient.newHttpClient();
            final var base = URI.create("http://localhost:" + server.instance().configuration().port());

            assertEquals(404, http.send(
                            HttpRequest.newBuilder()
                                    .GET()
                                    .uri(base)
                                    .build(),
                            discarding())
                    .statusCode());

            final var okGET = http.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(base.resolve("/test"))
                            .build(),
                    ofString());
            assertEquals(202, okGET.statusCode());
            assertEquals("get", okGET.headers().firstValue("x-test").orElse(null));
            assertEquals("{\"hello\":true}", okGET.body());

            final var okPOST = http.send(
                    HttpRequest.newBuilder()
                            .POST(HttpRequest.BodyPublishers.ofString("{\"hello\":true}"))
                            .uri(base.resolve("/test"))
                            .build(),
                    ofString());
            assertEquals(203, okPOST.statusCode());
            assertEquals("post", okPOST.headers().firstValue("x-test").orElse(null));
            assertEquals("}eurt:\"olleh\"{", okPOST.body());
        }
    }

    private static class GETEndpointBean extends BaseBean<Endpoint> {
        protected GETEndpointBean() {
            super(Endpoint.class, DefaultScoped.class, 1000, Map.of());
        }

        @Override
        public Endpoint create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return new Endpoint() {
                @Override
                public boolean matches(final Request request) {
                    return "GET".equalsIgnoreCase(request.method()) && request.path().contains("/test");
                }

                @Override
                public CompletionStage<Response> handle(final Request request) {
                    return completedFuture(Response.of()
                            .status(202)
                            .header("X-Test", "get")
                            .body("{\"hello\":true}")
                            .build());
                }
            };
        }
    }

    private static class POSTEndpointBean extends BaseBean<Endpoint> {
        protected POSTEndpointBean() {
            super(Endpoint.class, DefaultScoped.class, 1000, Map.of());
        }

        @Override
        public Endpoint create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return new Endpoint() {
                @Override
                public boolean matches(final Request request) {
                    return "POST".equalsIgnoreCase(request.method()) && request.path().contains("/test");
                }

                @Override
                public CompletionStage<Response> handle(final Request request) {
                    return completedFuture(Response.of()
                            .status(203)
                            .header("X-Test", "post")
                            .body(new StringBuilder(read(request.fullBody())).reverse().toString())
                            .build());
                }
            };
        }

        private String read(final Flow.Publisher<ByteBuffer> body) {
            final var list = new ArrayList<ByteBuffer>();
            final var awaiter = new CountDownLatch(1);
            body.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(final Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(final ByteBuffer item) {
                    list.add(item);
                }

                @Override
                public void onError(final Throwable throwable) {
                    list.clear(); // make it likely fail
                    awaiter.countDown();
                }

                @Override
                public void onComplete() {
                    awaiter.countDown();
                }
            });
            try {
                awaiter.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            }
            return ByteBuffers.asString(list);
        }
    }
}
