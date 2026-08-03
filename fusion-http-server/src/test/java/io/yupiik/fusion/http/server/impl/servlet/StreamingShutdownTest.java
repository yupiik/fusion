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
package io.yupiik.fusion.http.server.impl.servlet;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.spi.Endpoint;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.net.http.HttpResponse.BodyHandlers.ofInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A response which never completes - a SSE channel for example - is an in progress async request, and the servlet
 * container awaits those before stopping its context. The server must therefore release them when it closes, else
 * every shutdown pays that grace period.
 */
class StreamingShutdownTest {
    @Test
    void closeReleasesAStreamingResponse() throws Exception {
        final var endpoint = new NeverEndingEndpoint();
        final var container = ConfiguringContainer.of()
                .register(new NeverEndingEndpointBean(endpoint))
                .start();
        final long elapsed;
        var closed = false;
        try {
            final var port = container.lookup(WebServer.class).instance().configuration().port();
            final var response = HttpClient.newHttpClient()
                    .send(
                            HttpRequest.newBuilder()
                                    .GET()
                                    .uri(URI.create("http://localhost:" + port + "/stream"))
                                    .build(),
                            ofInputStream());

            // the response is committed - the endpoint emitted its first chunk - but the stream stays open
            assertEquals(200, response.statusCode());
            assertTrue(endpoint.subscribed.await(5, TimeUnit.SECONDS), "the body must have been subscribed to");

            // closing the container destroys the web server bean, i.e. what stops the container for real - it is the
            // measured operation so it cannot be delegated to a try-with-resources
            final var start = System.nanoTime();
            container.close();
            closed = true;
            elapsed = System.nanoTime() - start;

            response.body().close();
        } finally {
            if (!closed) { // the test failed before closing it, do not leak the container nor its tomcat
                container.close();
            }
        }

        assertTrue(endpoint.cancelled.get(), "the publisher must have been cancelled");
        // tomcat awaits the in progress async requests for unloadDelay - 2s by default - so a leaked stream is
        // immediately visible here
        final var millis = TimeUnit.NANOSECONDS.toMillis(elapsed);
        assertTrue(millis < 1_500, () -> "closing took " + millis + "ms, the streaming response was not released");
    }

    @Test
    void aStoppingServletRefusesNewRequests() {
        final var servlet = new FusionServlet(List.of());
        final var status = new AtomicInteger();
        final var response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class},
                (proxy, method, args) -> {
                    if ("setStatus".equals(method.getName())) {
                        status.set((int) args[0]);
                    }
                    return null;
                });

        servlet.cancelActiveStreams(); // what the web server calls when it starts to close

        servlet.service(null, response);

        // refusing right away is what keeps a late request from registering a stream the shutdown already swept -
        // the container would then wait for it again
        assertEquals(503, status.get());
    }

    private static class NeverEndingEndpoint implements Endpoint {
        private final CountDownLatch subscribed = new CountDownLatch(1);
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public boolean matches(final Request request) {
            return "/stream".equals(request.path());
        }

        @Override
        public CompletionStage<Response> handle(final Request request) {
            return completedFuture(Response.of()
                    .status(200)
                    .header("content-type", "text/event-stream")
                    .body((Flow.Publisher<ByteBuffer>) subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                        private final AtomicBoolean greeted = new AtomicBoolean();

                        @Override
                        public void request(final long n) {
                            // commit the response once then stay silent, like a SSE channel awaiting an event
                            if (greeted.compareAndSet(false, true)) {
                                subscriber.onNext(ByteBuffer.wrap(": ping\n\n".getBytes(UTF_8)));
                                subscribed.countDown();
                            }
                        }

                        @Override
                        public void cancel() {
                            cancelled.set(true);
                        }
                    }))
                    .build());
        }
    }

    @DefaultScoped
    private static class NeverEndingEndpointBean extends BaseBean<Endpoint> {
        private final Endpoint endpoint;

        private NeverEndingEndpointBean(final Endpoint endpoint) {
            super(Endpoint.class, DefaultScoped.class, 1000, Map.of());
            this.endpoint = endpoint;
        }

        @Override
        public Endpoint create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return endpoint;
        }
    }
}
