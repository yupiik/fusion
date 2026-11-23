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

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.yupiik.kubernetes.operator.base.test.sample.FusionGeneratedModule.SampleResource$FusionJsonCodec;
import static io.yupiik.kubernetes.operator.base.test.sample.FusionGeneratedModule.SampleResource$Metadata$FusionJsonCodec;
import static io.yupiik.kubernetes.operator.base.test.sample.FusionGeneratedModule.SampleResource$Spec$FusionJsonCodec;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
class OperatorRuntimeRecoveryTest {
    @Test
    void httpExpiryDeletesMissingItemsBeforeCheckpointWithoutClearingOtherNamespace(@TempDir final Path storage) throws Exception {
        expiry(storage, false);
    }

    @Test
    void inBandExpiryDeletesMissingItemsBeforeCheckpointWithoutClearingOtherNamespace(@TempDir final Path storage) throws Exception {
        expiry(storage, true);
    }

    private void expiry(final Path storage, final boolean inBand) throws Exception {
        final var deleted = new CopyOnWriteArrayList<String>();
        final var runtime = new AtomicReference<InspectableRuntime>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onDelete(final SampleResource resource) {
                assertFalse(Files.exists(storage.resolve("samples-one")), "fresh checkpoint must follow deletion");
                assertEquals(List.of("B"), runtime.get().items(), "controller state must be clean before checkpoint");
                deleted.add(resource.metadata().uid());
            }
        })) {
            runtime.set(fixture.runtime);
            fixture.start();
            fixture.client.next().respond(200, snapshot("initial-one", object("A", "one", "1")), true);
            fixture.client.next().respond(200, snapshot("initial-two", object("B", "two", "1")), true);
            final var first = fixture.client.next();
            final var second = fixture.client.next();
            final var one = first.request.uri().getPath().contains("/one/") ? first : second;
            final var two = one == first ? second : first;
            two.respond(200, "", false);
            one.respond(200, "", true); // disconnected; A is deleted while offline
            fixture.scheduler.retry();
            final var resumed = fixture.client.next();
            assertTrue(resumed.request.uri().getQuery().contains("resourceVersion=initial-one"));
            resumed.respond(inBand ? 200 : 410, inBand ?
                    "{\"type\":\"ERROR\",\"object\":{\"code\":410}}\n" : "{}", true);
            fixture.scheduler.retry();
            final var relist = fixture.client.next();
            assertNull(relist.request.uri().getQuery());
            relist.respond(200, snapshot("fresh", ""), true);
            fixture.client.next(); // fresh watch is connected only after reconciliation/checkpoint
            assertEquals(List.of("A"), deleted);
            assertEquals(List.of("B"), fixture.runtime.items());
            assertEquals("{\"resourceVersion\":\"fresh\"}", Files.readString(storage.resolve("samples-one")));
            assertEquals("{\"resourceVersion\":\"initial-two\"}", Files.readString(storage.resolve("samples-two")));
        }
    }

    @Test
    void restartRetainsKnownItemsForExpiredCursorReconciliation(@TempDir final Path storage) throws Exception {
        try (final var fixture = new Fixture(storage, 1, new TestOperator())) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("old", object("A", "one", "1")), true);
            fixture.client.next().respond(200, snapshot("other", ""), true);
            fixture.client.next();
            fixture.client.next();
        }
        final var deleted = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onDelete(final SampleResource resource) {
                deleted.add(resource.metadata().uid());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(410, "{}", true);
            fixture.client.next().respond(200, snapshot("other", ""), true);
            fixture.client.next(); // namespace two watch
            fixture.scheduler.retry();
            fixture.client.next().respond(200, snapshot("fresh", ""), true);
            fixture.client.next();
            assertEquals(List.of("A"), deleted);
            assertEquals(List.of(), fixture.runtime.items());
        }
    }

    @Test
    void busyWatchYieldsToOtherNamespaceEventBookmarkAndReconnect() throws Exception {
        fairness(false);
    }

    @Test
    void shutdownStillDrainsAcceptedWatchWorkAcrossBatchYields() throws Exception {
        fairness(true);
    }

    private void fairness(final boolean stop) throws Exception {
        final var entered = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var callbacks = new CopyOnWriteArrayList<String>();
        final var countAtBookmark = new AtomicInteger(-1);
        final var bookmark = new CountDownLatch(1);
        try (final var fixture = new Fixture(null, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource resource) {
                if ("0".equals(resource.metadata().resourceVersion())) {
                    entered.countDown();
                    await(release);
                }
                callbacks.add(resource.metadata().namespace() + ":" + resource.metadata().resourceVersion());
            }

            @Override
            public void onBookmark(final String version) {
                countAtBookmark.set(callbacks.size());
                bookmark.countDown();
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("one", ""), true);
            fixture.client.next().respond(200, snapshot("two", ""), true);
            final var first = fixture.client.next();
            final var second = fixture.client.next();
            final var one = first.request.uri().getPath().contains("/one/") ? first : second;
            final var two = one == first ? second : first;
            one.respond(200, event("A", "one", "0"), false);
            assertTrue(entered.await(5, SECONDS));
            // Synchronous subscriber delivery ensures all this work is accepted while A's callback is blocked.
            for (int i = 1; i < 256; i++) {
                one.emit(event("A", "one", Integer.toString(i)));
            }
            two.respond(200, event("B", "two", "b") +
                    "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"b-mark\"}}}\n", true);
            final var stopping = stop ? CompletableFuture.runAsync(fixture.runtime::stop) : null;
            if (stop) {
                fixture.runtime.awaitAdmissionClosed();
            }
            release.countDown();
            assertTrue(bookmark.await(5, SECONDS));
            if (stopping != null) {
                stopping.get(7, SECONDS);
            } else {
                fixture.scheduler.retry(); // B's EOF must not be stuck behind A's backlog
                assertTrue(fixture.client.next().request.uri().getQuery().contains("b-mark"));
                fixture.runtime.stop();
            }
            assertTrue(countAtBookmark.get() < 257, "B's event/bookmark must run before A exhausts its backlog");
            assertEquals(257, callbacks.size(), "accepted work must survive batch resubmission during stop");
            assertEquals(range(0, 256).mapToObj(i -> "one:" + i).toList(),
                    callbacks.stream().filter(it -> it.startsWith("one:")).toList());
        } finally {
            release.countDown();
        }
    }

    @Test
    void multipleWorkersStillUseOneOrderedDrainerPerWatch() throws Exception {
        final var entered = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var bookmark = new CountDownLatch(1);
        final var versions = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(null, 2, new TestOperator() {
            @Override
            public void onAdd(final SampleResource resource) {
                if ("0".equals(resource.metadata().resourceVersion())) {
                    entered.countDown();
                    await(release);
                }
                versions.add(resource.metadata().resourceVersion());
            }

            @Override
            public void onBookmark(final String version) {
                bookmark.countDown();
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("one", ""), true);
            fixture.client.next().respond(200, snapshot("two", ""), true);
            final var first = fixture.client.next();
            final var second = fixture.client.next();
            final var one = first.request.uri().getPath().contains("/one/") ? first : second;
            final var two = one == first ? second : first;
            one.respond(200, event("A", "one", "0"), false);
            assertTrue(entered.await(5, SECONDS));
            for (int i = 1; i < 256; i++) {
                one.emit(event("A", "one", Integer.toString(i)));
            }
            two.respond(200, "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"b-mark\"}}}\n", false);
            assertTrue(bookmark.await(5, SECONDS));
            assertEquals(List.of(), versions, "another worker must not overtake the blocked watch callback");
            release.countDown();
            fixture.runtime.stop();
            assertEquals(range(0, 256).mapToObj(Integer::toString).toList(), versions);
        } finally {
            release.countDown();
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, SECONDS));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String snapshot(final String version, final String items) {
        return "{\"metadata\":{\"resourceVersion\":\"" + version + "\"},\"items\":[" + items + "]}";
    }

    private static String object(final String uid, final String namespace, final String version) {
        return "{\"metadata\":{\"uid\":\"" + uid + "\",\"namespace\":\"" + namespace +
                "\",\"resourceVersion\":\"" + version + "\"},\"spec\":{\"message\":\"test\"}}";
    }

    private static String event(final String uid, final String namespace, final String version) {
        return "{\"type\":\"ADDED\",\"object\":" + object(uid, namespace, version) + "}\n";
    }

    private static class TestOperator extends Operator.Base<SampleResource> {
        private TestOperator() {
            super(SampleResource.class, new Operator.DefaultOperatorConfiguration(true, List.of("one", "two"), "samples", "test.io/v1"));
        }
    }

    private static class Fixture implements AutoCloseable {
        private final ManualScheduler scheduler = new ManualScheduler();
        private final ControlledClient client = new ControlledClient();
        private final JsonMapperImpl mapper = new JsonMapperImpl(List.of(
                FusionGeneratedModule.Event$FusionJsonCodec(), FusionGeneratedModule.OperatorRuntime$EventList$FusionJsonCodec(),
                FusionGeneratedModule.OperatorRuntime$ListMetadata$FusionJsonCodec(), FusionGeneratedModule.OperatorRuntime$OperatorState$FusionJsonCodec(),
                SampleResource$FusionJsonCodec(), SampleResource$Metadata$FusionJsonCodec(), SampleResource$Spec$FusionJsonCodec()), key -> Optional.empty());
        private final InspectableRuntime runtime;

        private Fixture(final Path storage, final int eventThreads, final TestOperator operator) {
            runtime = new InspectableRuntime(new OperatorConfiguration(true, null, true, eventThreads, -1, false,
                    storage == null ? null : storage.toString()), client, scheduler, mapper, operator);
        }

        private void start() {
            runtime.doStart().toCompletableFuture().join();
        }

        @Override
        public void close() {
            runtime.stop();
            client.close();
            scheduler.shutdownNow();
            mapper.close();
        }
    }

    private static class InspectableRuntime extends OperatorRuntime<SampleResource> {
        private InspectableRuntime(final OperatorConfiguration configuration, final ControlledClient client,
                                   final ManualScheduler scheduler, final JsonMapperImpl mapper, final TestOperator operator) {
            super(configuration, client, scheduler, mapper, operator);
        }

        private void awaitAdmissionClosed() {
            final var deadline = System.nanoTime() + SECONDS.toNanos(5);
            while (!stopping && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertTrue(stopping, "shutdown must close admission before the blocked callback is released");
        }

        private List<String> items() {
            return state.items().stream().map(it -> it.metadata().uid()).toList();
        }
    }

    private static class ManualScheduler extends ScheduledThreadPoolExecutor {
        private final BlockingQueue<Runnable> retries = new LinkedBlockingQueue<>();

        private ManualScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
            retries.add(command);
            return super.schedule(() -> { }, 1, TimeUnit.DAYS);
        }

        private void retry() throws InterruptedException {
            final var task = retries.poll(5, SECONDS);
            assertNotNull(task, "retry must have been scheduled");
            task.run();
        }
    }

    private static class ControlledClient extends KubernetesClient {
        private final BlockingQueue<Exchange<?>> requests = new LinkedBlockingQueue<>();

        private ControlledClient() {
            super(new KubernetesClientConfiguration().setMaster("http://localhost").setToken("none"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
            final var exchange = new Exchange<>(request, handler);
            requests.add(exchange);
            return exchange.future;
        }

        private Exchange<?> next() throws InterruptedException {
            final var request = requests.poll(5, SECONDS);
            assertNotNull(request, "expected request");
            return request;
        }
    }

    private static class Exchange<T> {
        private final HttpRequest request;
        private final HttpResponse.BodyHandler<T> handler;
        private final CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
        private HttpResponse.BodySubscriber<T> subscriber;

        private Exchange(final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
            this.request = request;
            this.handler = handler;
        }

        private void respond(final int status, final String body, final boolean complete) {
            subscriber = handler.apply(new HttpResponse.ResponseInfo() {
                @Override public int statusCode() { return status; }
                @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
                @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
            });
            subscriber.getBody().whenComplete((value, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                } else {
                    future.complete(new Response<>(request, status, value));
                }
            });
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override public void request(final long count) { }
                @Override public void cancel() { }
            });
            emit(body);
            if (complete) {
                subscriber.onComplete();
            }
        }

        private void emit(final String body) {
            if (!body.isEmpty()) {
                subscriber.onNext(List.of(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8))));
            }
        }
    }

    private record Response<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
