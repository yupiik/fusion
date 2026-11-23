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
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration;
import io.yupiik.kubernetes.operator.base.impl.FusionGeneratedModule;
import io.yupiik.kubernetes.operator.base.impl.OperatorConfiguration;
import io.yupiik.kubernetes.operator.base.impl.OperatorRuntime;
import io.yupiik.kubernetes.operator.base.spi.Operator;
import io.yupiik.kubernetes.operator.base.test.sample.SampleResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static io.yupiik.kubernetes.operator.base.test.sample.FusionGeneratedModule.SampleResource$FusionJsonCodec;
import static io.yupiik.kubernetes.operator.base.test.sample.FusionGeneratedModule.SampleResource$Metadata$FusionJsonCodec;
import static io.yupiik.kubernetes.operator.base.test.sample.FusionGeneratedModule.SampleResource$Spec$FusionJsonCodec;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(20)
class OperatorRuntimeTest {
    @Test
    void bookmarkUsesObjectMetadataAndOpaqueToken(@TempDir final Path storage) throws Exception {
        final var queries = new CopyOnWriteArrayList<String>();
        final var bookmarks = new CopyOnWriteArrayList<String>();
        final var requests = new AtomicInteger();
        final var token = "opaque/+ =version";
        try (final var fixture = new Fixture(storage, true, 1, new TestOperator() {
            @Override
            public void onBookmark(final String version) {
                bookmarks.add(version);
            }
        }, exchange -> {
            if (isWatch(exchange)) {
                queries.add(exchange.getRequestURI().getRawQuery());
                send(exchange, 200, requests.getAndIncrement() == 0 ?
                        "{\"type\":\"BOOKMARK\",\"metadata\":{\"resourceVersion\":\"WRONG\"}," +
                                "\"object\":{\"metadata\":{\"resourceVersion\":\"" + token + "\"}}}\n" : "");
            } else {
                send(exchange, 200, "{\"items\":[]}");
            }
        })) {
            fixture.start();
            until(() -> queries.size() >= 2);
            assertEquals(List.of(token), bookmarks);
            assertEquals(token, URLDecoder.decode(queries.get(1).split("resourceVersion=", 2)[1], StandardCharsets.UTF_8));
            assertEquals("{\"resourceVersion\":\"" + token + "\"}", Files.readString(storage.resolve("samples")));
        }
    }

    @Test
    void reconnectsAfterNormalEofWithBackoff() throws Exception {
        reconnects(false);
    }

    @Test
    void reconnectsAfterTransportFailureWithBackoff() throws Exception {
        reconnects(true);
    }

    private void reconnects(final boolean failure) throws Exception {
        try (final var fixture = new Fixture(null, true, 1, new TestOperator(), exchange -> {
            if (!isWatch(exchange)) {
                send(exchange, 200, "{\"items\":[]}");
            } else {
                if (failure) {
                    try (exchange) {
                        exchange.sendResponseHeaders(200, 1_024);
                        exchange.getResponseBody().write("\n".getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    send(exchange, 200, "");
                }
            }
        })) {
            fixture.start();
            final var arrivals = fixture.watchAttempts;
            until(() -> arrivals.size() >= 4);
            // Measure API calls, not the JDK client's transparent HTTP retries.
            // Empty or failed streams must back off rather than resetting on subscription.
            for (int i = 1; i < 4; i++) {
                assertTrue(arrivals.get(i) - arrivals.get(i - 1) >= MILLISECONDS.toNanos(70L << (i - 1)),
                        "Reconnect intervals (ns): " + arrivals.stream().map(time -> time - arrivals.get(0)).toList());
            }
            fixture.runtime.stop();
            final var count = arrivals.size();
            Thread.sleep(300);
            assertEquals(count, arrivals.size());
        }
    }

    @Test
    void failedHandlersAndMalformedEventsDoNotKillWorkersOrStarveHttp() throws Exception {
        final var events = new CopyOnWriteArrayList<String>();
        final var response = new AtomicInteger();
        // Four event workers and only one HTTP/scheduler thread would starve the old shared-pool loop.
        try (final var fixture = new Fixture(null, false, 4, new TestOperator() {
            @Override
            public void onAdd(final SampleResource resource) {
                events.add("add:" + resource.metadata().resourceVersion());
                throw new IllegalStateException("intentional handler failure");
            }

            @Override
            public void onModify(final SampleResource resource) {
                events.add("modify:" + resource.metadata().resourceVersion());
            }

            @Override
            public void onDelete(final SampleResource resource) {
                events.add("delete:" + resource.metadata().resourceVersion());
            }
        }, exchange -> send(exchange, 200, !isWatch(exchange) ? "{\"items\":[]}" :
                response.getAndIncrement() == 0 ? "not-json\n" + event("ADDED", "z-token") +
                        event("MODIFIED", "10") + event("DELETED", "2") : ""))) {
            fixture.start();
            until(() -> events.size() == 3 && response.get() >= 2);
            assertEquals(List.of("add:z-token", "modify:10", "delete:2"), events);
        }
    }

    @Test
    void expiredPersistedTokenIsClearedBeforeFullRelist(@TempDir final Path storage) throws Exception {
        Files.writeString(storage.resolve("samples"), "{\"resourceVersion\":\"expired-token\"}");
        final var adds = new CopyOnWriteArrayList<String>();
        final var requests = new CopyOnWriteArrayList<String>();
        final var cleared = new AtomicReference<Boolean>();
        try (final var fixture = new Fixture(storage, true, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource resource) {
                adds.add(resource.metadata().resourceVersion());
            }
        }, exchange -> {
            final var uri = exchange.getRequestURI().toString();
            requests.add(uri);
            if (uri.contains("expired-token")) {
                send(exchange, 410, "{}");
            } else if (!isWatch(exchange)) {
                cleared.set(!Files.exists(storage.resolve("samples")));
                send(exchange, 200, "{\"metadata\":{\"resourceVersion\":\"fresh-list\"},\"items\":[" +
                        object("item-one") + "," + object("item-two") + "]}");
            } else {
                send(exchange, 200, "");
            }
        })) {
            fixture.start();
            until(() -> requests.stream().anyMatch(uri -> uri.contains("watch=true")));
            assertEquals(Boolean.TRUE, cleared.get());
            assertEquals(List.of("item-one", "item-two"), adds);
            assertTrue(requests.stream().anyMatch(uri -> uri.contains("watch=true") && uri.contains("resourceVersion=fresh-list")));
            assertFalse(requests.stream().anyMatch(uri -> uri.contains("limit=")));
        }
    }

    @Test
    void expiredHttpWatchClearsResumeAndRelists(@TempDir final Path storage) throws Exception {
        expiredWatch(storage, false);
    }

    @Test
    void expiredInBandWatchClearsResumeAndRelists(@TempDir final Path storage) throws Exception {
        expiredWatch(storage, true);
    }

    private void expiredWatch(final Path storage, final boolean inBand) throws Exception {
        final var lists = new AtomicInteger();
        final var watches = new CopyOnWriteArrayList<String>();
        final var cleared = new AtomicReference<Boolean>();
        final var deleted = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, true, 1, new TestOperator() {
            @Override
            public void onDelete(final SampleResource resource) {
                assertFalse(Files.exists(storage.resolve("samples")), "delete must precede the fresh-list checkpoint");
                deleted.add(resource.metadata().resourceVersion());
            }
        }, exchange -> {
            if (!isWatch(exchange)) {
                final var index = lists.incrementAndGet();
                if (index == 2) {
                    cleared.set(!Files.exists(storage.resolve("samples")) && exchange.getRequestURI().getQuery() == null);
                }
                send(exchange, 200, "{\"metadata\":{\"resourceVersion\":\"list-" + index + "\"},\"items\":[" +
                        (index == 1 ? object("A") : "") + "]}");
            } else {
                watches.add(exchange.getRequestURI().getQuery());
                if (watches.size() == 2) {
                    // A was deleted while the first connection was offline; its cursor has expired.
                    send(exchange, inBand ? 200 : 410, inBand ?
                            "{\"type\":\"ERROR\",\"object\":{\"kind\":\"Status\",\"code\":410}}\n" : "{}");
                } else {
                    send(exchange, 200, "");
                }
            }
        })) {
            fixture.start();
            until(() -> watches.size() >= 3);
            assertEquals(Boolean.TRUE, cleared.get());
            assertEquals(List.of("A"), deleted);
            assertEquals(2, lists.get());
            assertTrue(watches.get(0).contains("resourceVersion=list-1"));
            assertTrue(watches.get(1).contains("resourceVersion=list-1"));
            assertTrue(watches.get(2).contains("resourceVersion=list-2"));
        }
    }

    @Test
    void namespacesHaveIndependentResumeTokens(@TempDir final Path storage) throws Exception {
        final var queries = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, true, 2, new TestOperator() {
            @Override
            public List<String> namespaces() {
                return List.of("one", "two");
            }
        }, exchange -> {
            final var namespace = exchange.getRequestURI().getPath().contains("/one/") ? "one" : "two";
            if (isWatch(exchange)) {
                queries.add(namespace + ":" + exchange.getRequestURI().getQuery());
                send(exchange, 200, "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"bookmark-" + namespace + "\"}}}\n");
            } else {
                send(exchange, 200, "{\"metadata\":{\"resourceVersion\":\"initial-" + namespace + "\"},\"items\":[]}");
            }
        })) {
            fixture.start();
            until(() -> queries.stream().anyMatch(q -> q.contains("one:") && q.contains("resourceVersion=bookmark-one")) &&
                    queries.stream().anyMatch(q -> q.contains("two:") && q.contains("resourceVersion=bookmark-two")));
            fixture.runtime.stop();
            assertEquals("{\"resourceVersion\":\"bookmark-one\"}", Files.readString(storage.resolve("samples-one")));
            assertEquals("{\"resourceVersion\":\"bookmark-two\"}", Files.readString(storage.resolve("samples-two")));
            assertFalse(Files.exists(storage.resolve("samples")));
        }
    }

    @Test
    void shutdownDrainsQueueReleasesAwaiterAndDoesNotCloseBorrowedResources() throws Exception {
        final var entered = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var callbacks = new CopyOnWriteArrayList<String>();
        final var stop = Executors.newSingleThreadExecutor();
        try (final var fixture = new Fixture(null, false, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource resource) {
                entered.countDown();
                try {
                    assertTrue(release.await(5, SECONDS));
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                callbacks.add("add");
            }

            @Override
            public void onDelete(final SampleResource resource) {
                callbacks.add("delete");
            }

            @Override
            public void onStop() {
                callbacks.add("stop");
            }
        }, exchange -> send(exchange, 200, "{\"items\":[]}"))) {
            // Directly accepted events make the drain boundary deterministic, without an HTTP race.
            fixture.runtime.init();
            fixture.runtime.onEvent(event("ADDED", "one"));
            fixture.runtime.onEvent(event("DELETED", "two"));
            assertTrue(entered.await(5, SECONDS));
            final var stopping = stop.submit(fixture.runtime::stop);
            assertFalse(stopping.isDone());
            release.countDown();
            stopping.get(5, SECONDS);
            fixture.runtime.doStop();
            stop.submit(fixture.runtime::await).get(1, SECONDS);
            assertEquals(List.of("add", "delete", "stop"), callbacks);
            assertFalse(fixture.scheduler.isShutdown());
            assertEquals(42, fixture.scheduler.submit(() -> 42).get(1, SECONDS));
            assertEquals(200, fixture.client.send(HttpRequest.newBuilder(URI.create("https://kubernetes.api/ownership")).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {
            release.countDown();
            stop.shutdownNow();
        }
    }

    @Test
    void shutdownInterruptsHandlerAfterBoundedDrain() throws Exception {
        final var entered = new CountDownLatch(1);
        final var interrupted = new CountDownLatch(1);
        try (final var fixture = new Fixture(null, false, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource resource) {
                entered.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (final InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            }
        }, exchange -> send(exchange, 200, "{}"))) {
            fixture.runtime.init();
            fixture.runtime.onEvent(event("ADDED", "one"));
            assertTrue(entered.await(5, SECONDS));
            final var start = System.nanoTime();
            fixture.runtime.stop();
            assertTrue(System.nanoTime() - start < SECONDS.toNanos(7));
            assertTrue(interrupted.await(1, SECONDS));
        }
    }

    private static boolean isWatch(final HttpExchange exchange) {
        return exchange.getRequestURI().getQuery() != null && exchange.getRequestURI().getQuery().contains("watch=true");
    }

    private static String object(final String version) {
        return "{\"metadata\":{\"uid\":\"test\",\"namespace\":\"default\",\"resourceVersion\":\"" + version +
                "\"},\"spec\":{\"message\":\"test\"}}";
    }

    private static String event(final String type, final String version) {
        return "{\"type\":\"" + type + "\",\"object\":" + object(version) + "}\n";
    }

    private static void send(final HttpExchange exchange, final int status, final String body) throws IOException {
        try (exchange) {
            final var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static void until(final BooleanSupplier condition) throws InterruptedException {
        final var deadline = System.nanoTime() + SECONDS.toNanos(7);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Condition not met before deadline");
    }

    private static class TestOperator extends Operator.Base<SampleResource> {
        private TestOperator() {
            super(SampleResource.class, new Operator.DefaultOperatorConfiguration(true, List.of("default"), "samples", "test.io/v1"));
        }
    }

    private static class Fixture implements AutoCloseable {
        private final List<Long> watchAttempts = new CopyOnWriteArrayList<>();
        private final HttpServer server;
        private final ExecutorService serverThreads = Executors.newCachedThreadPool();
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        private final JsonMapperImpl mapper = new JsonMapperImpl(List.of(
                FusionGeneratedModule.Event$FusionJsonCodec(),
                FusionGeneratedModule.OperatorRuntime$EventList$FusionJsonCodec(),
                FusionGeneratedModule.OperatorRuntime$ListMetadata$FusionJsonCodec(),
                FusionGeneratedModule.OperatorRuntime$OperatorState$FusionJsonCodec(),
                SampleResource$FusionJsonCodec(), SampleResource$Metadata$FusionJsonCodec(), SampleResource$Spec$FusionJsonCodec()),
                key -> Optional.empty());
        private final KubernetesClient client;
        private final OperatorRuntime<SampleResource> runtime;

        private Fixture(final Path storage, final boolean bookmarks, final int eventThreads,
                        final TestOperator operator, final HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(serverThreads);
            server.createContext("/", handler);
            server.start();
            client = new KubernetesClient(new KubernetesClientConfiguration()
                    .setMaster("http://localhost:" + server.getAddress().getPort())
                    .setToken("target/missing-token")
                    .setClientCustomizer(builder -> builder.executor(scheduler))) {
                @Override
                public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                                       final HttpResponse.BodyHandler<T> handler) {
                    if (request.uri().getQuery() != null && request.uri().getQuery().contains("watch=true")) {
                        watchAttempts.add(System.nanoTime());
                    }
                    return super.sendAsync(request, handler);
                }
            };
            runtime = new OperatorRuntime<>(new OperatorConfiguration(true, null, bookmarks, eventThreads, -1, true,
                    storage == null ? null : storage.toString()), client, scheduler, mapper, operator);
        }

        private void start() throws Exception {
            runtime.doStart().toCompletableFuture().get(5, SECONDS);
        }

        @Override
        public void close() throws Exception {
            runtime.doStop();
            client.close();
            scheduler.shutdownNow();
            server.stop(0);
            serverThreads.shutdownNow();
            mapper.close();
        }
    }
}
