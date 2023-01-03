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
package io.yupiik.fusion.tracing.server;

import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import io.yupiik.fusion.tracing.collector.AccumulatingSpanCollector;
import io.yupiik.fusion.tracing.id.IdGenerator;
import io.yupiik.fusion.tracing.span.Span;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static java.time.Clock.systemUTC;
import static java.util.Comparator.comparingLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TracingValveTest {
    @Test
    void run() throws IOException, InterruptedException {
        final var spans = new ArrayList<Span>();

        final var serverConf = WebServer.Configuration.of().port(0);
        final var configuration = serverConf.unwrap(TomcatWebServerConfiguration.class);
        configuration.setInitializers(List.of((ignore, ctx) -> {
            // sync
            ctx.addServlet("test", new HttpServlet() {
                @Override
                protected void service(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
                    resp.getWriter().write("ok from servlet");
                }
            }).addMapping("/test");

            // async
            final var async = ctx.addServlet("async-test", new HttpServlet() {
                @Override
                protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
                    final var async = req.startAsync();
                    final var latch = new CountDownLatch(1);
                    req.setAttribute(CountDownLatch.class.getName(), latch);
                    new Thread(() -> {
                        try {
                            latch.await();
                        } catch (final InterruptedException e) {
                            throw new IllegalStateException(e);
                        }
                        try {
                            resp.getWriter().write("ok from async servlet");
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        } finally {
                            async.complete();
                        }
                    }, getClass().getName() + "-servlet-thread").start();
                }
            });
            async.setAsyncSupported(true);
            async.addMapping("/async-test");
        }));
        configuration.setContextCustomizers(List.of(c -> c.getPipeline().addValve(new TracingValve(
                new ServerTracingConfiguration(),
                new AccumulatingSpanCollector().setOnFlush(spans::addAll),
                new IdGenerator(IdGenerator.Type.COUNTER),
                systemUTC()) {
            @Override
            public void invoke(final Request request, final Response response) throws IOException, ServletException {
                try {
                    super.invoke(request, response);
                } finally { // ensure we start the async response after the tracing is returned so we can test the async behavior
                    final var latch = request.getAttribute(CountDownLatch.class.getName());
                    if (latch != null) {
                        ((CountDownLatch) latch).countDown();
                    }
                }
            }
        })));
        try (final var server = WebServer.of(serverConf)) {
            final var client = HttpClient.newHttpClient();
            final var base = URI.create("http://localhost:" + server.configuration().port());
            assertEquals("ok from servlet", client.send(
                    HttpRequest.newBuilder().GET().uri(base.resolve("/test")).build(),
                    HttpResponse.BodyHandlers.ofString()).body());
            assertEquals("ok from async servlet", client.send(
                    HttpRequest.newBuilder().GET().uri(base.resolve("/async-test")).build(),
                    HttpResponse.BodyHandlers.ofString()).body());
        }

        assertEquals(2, spans.size());

        final var iterator = spans.stream().sorted(comparingLong(s -> ((Number) s.id()).longValue())).iterator();

        final var span1 = iterator.next();
        assertNull(span1.parentId());
        assertEquals(2L, span1.traceId());
        assertEquals(1L, span1.id());
        assertEquals("server", span1.name());
        assertEquals("SERVER", span1.kind());
        assertEquals(Map.of(
                "component", "tomcat",
                "http.method", "GET",
                "http.url", "/test",
                "http.status", 200
        ), span1.tags());
        assertNotNull(span1.timestamp());
        assertNotNull(span1.duration());
        assertNotNull(span1.localEndpoint());

        final var span2 = iterator.next();
        assertNull(span2.parentId());
        assertEquals(4L, span2.traceId());
        assertEquals(3L, span2.id());
        assertEquals("server", span2.name());
        assertEquals("SERVER", span2.kind());
        assertEquals(Map.of(
                "component", "tomcat",
                "http.method", "GET",
                "http.url", "/async-test",
                "http.status", 200
        ), span2.tags());
        assertNotNull(span2.timestamp());
        assertNotNull(span2.duration());
        assertNotNull(span1.localEndpoint());
    }
}
