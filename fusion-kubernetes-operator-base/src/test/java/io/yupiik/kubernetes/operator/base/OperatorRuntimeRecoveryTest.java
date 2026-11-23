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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.yupiik.kubernetes.operator.base.test.sample.FusionGeneratedModule.SampleResource$FusionJsonCodec;
import static io.yupiik.kubernetes.operator.base.test.sample.FusionGeneratedModule.SampleResource$Metadata$FusionJsonCodec;
import static io.yupiik.kubernetes.operator.base.test.sample.FusionGeneratedModule.SampleResource$Spec$FusionJsonCodec;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(15)
class OperatorRuntimeRecoveryTest {
    @Test
    void legacyIngressDiagnosticsOmitBodiesUrlsAndExceptionsWithoutChangingCallbacks() throws Exception {
        final var records = new CopyOnWriteArrayList<LogRecord>();
        final var errors = new CopyOnWriteArrayList<String>();
        final var completed = new CountDownLatch(1);
        final var error = "{\"type\":\"ERROR\",\"object\":{\"message\":\"raw-ingress-secret\"}}";
        final var logger = Logger.getLogger(InspectableRuntime.class.getName());
        final var handler = new Handler() {
            @Override public void publish(final LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try (final var fixture = new Fixture(null, 1, new TestOperator() {
            @Override
            public void onError(final String line) {
                errors.add(line);
            }

            @Override
            public void onAdd(final SampleResource item) {
                throw new IllegalStateException("https://user:raw-ingress-secret@server/?token=raw-ingress-secret",
                        new IOException("Authorization: Bearer raw-ingress-secret"));
            }

            @Override
            public void onBookmark(final String version) {
                completed.countDown();
            }
        })) {
            fixture.start();
            fixture.runtime.legacyEvent("{raw-ingress-secret");
            fixture.runtime.legacyEvent("{\"object\":{\"message\":\"raw-ingress-secret\"}}");
            fixture.runtime.legacyEvent("{\"type\":\"raw-ingress-secret\",\"object\":{}}");
            fixture.runtime.legacyEvent(error);
            fixture.runtime.legacyEvent(event("A", "one", "1"));
            fixture.runtime.onEvent("{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"done\"}}}");
            assertTrue(completed.await(5, SECONDS)); // raw-ingress failure still permits subsequent callbacks
            assertEquals(List.of(error), errors);
            assertFalse(records.isEmpty());
            assertTrue(records.stream().allMatch(record -> record.getThrown() == null));
            assertTrue(records.stream().noneMatch(record -> record.getMessage().contains("raw-ingress-secret") ||
                    record.getMessage().contains("https://") || record.getMessage().contains("Authorization")));
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
    }

    @Test
    void asyncDiagnosticsAreBoundedCredentialSafeAndCancellationIsQuiet() throws Exception {
        final var spec = new TestOperator() { };
        final var records = new CopyOnWriteArrayList<LogRecord>();
        final var leaks = new CopyOnWriteArrayList<LogRecord>();
        final var logger = Logger.getLogger(InspectableRuntime.class.getName());
        final var handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                if (record.getMessage().contains("diagnostic-secret-marker")) {
                    leaks.add(record);
                }
                if (record.getMessage().contains("operator=" + spec.getClass().getName() + ",")) {
                    records.add(record);
                }
            }

            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(handler);
        try (final var fixture = new Fixture(null, 1, spec)) {
            fixture.start();
            fixture.client.next().future.completeExceptionally(new IOException("secret https://user:password@server?token=credential"));
            fixture.client.next().respond(200, snapshot("two", ""), true);
            fixture.client.next();
            for (int i = 0; i < 11; i++) {
                fixture.scheduler.retry();
                fixture.client.next().respond(503, "secret body Authorization: Bearer credential", true);
            }
            fixture.scheduler.retry(); // scheduling is ordered after the diagnostic
            assertEquals(5, records.size()); // first four, then eighth failure
            assertTrue(records.stream().allMatch(record -> record.getThrown() == null && record.getMessage().length() < 800));
            assertTrue(records.stream().anyMatch(record -> record.getMessage().contains("status=503") && record.getMessage().contains("retry in")));
            assertTrue(records.stream().noneMatch(record -> record.getMessage().contains("secret") || record.getMessage().contains("credential") ||
                    record.getMessage().contains("password") || record.getMessage().contains("https://")));
            fixture.client.next().respond(200, snapshot("one", ""), true);
            final var watch = fixture.client.next();
            watch.respond(200, "{\"type\":\"UNKNOWN\",\"object\":{\"diagnostic-secret-marker\":true}}\n" +
                    "{\"object\":{\"diagnostic-secret-marker\":true}}\n" +
                    "{\"type\":\"diagnostic-secret-marker\",\"object\":null}\n", false);
            watch.future.cancel(true);
            fixture.runtime.stop();
            assertEquals(5, records.size());
            assertTrue(leaks.isEmpty());
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
    }

    @Test
    void retryAfterHttpDateIsHonoredWithinCap() throws Exception {
        try (final var fixture = new Fixture(null, 1, new TestOperator())) {
            fixture.start();
            final var retryAt = ZonedDateTime.now(ZoneOffset.UTC).plusHours(1)
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME);
            fixture.client.next().respond(503, "", true, Map.of("Retry-After", List.of(retryAt)));
            fixture.client.next().respond(200, snapshot("two", ""), true);
            fixture.client.next();
            fixture.scheduler.retry();
            assertEquals(List.of(5_000L), fixture.scheduler.delays);
            fixture.client.next();
        }
    }

    @Test
    void liveCallbackFailureCannotBeSkippedByLaterBookmark(@TempDir final Path storage) throws Exception {
        final var otherBookmark = new CountDownLatch(1);
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                if ("one".equals(item.metadata().namespace()) && "2".equals(item.metadata().resourceVersion())) {
                    throw new IllegalStateException("failed callback");
                }
            }

            @Override
            public void onBookmark(final String version) {
                if ("other".equals(version)) {
                    otherBookmark.countDown();
                }
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("old", object("A", "one", "1")), true);
            fixture.client.next().respond(200, snapshot("two", ""), true);
            final var first = fixture.client.next();
            final var second = fixture.client.next();
            final var one = first.request.uri().getPath().contains("/one/") ? first : second;
            final var two = one == first ? second : first;
            one.respond(200, event("A", "one", "2") +
                    "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"must-not-save\"}}}\n", false);
            two.respond(200, "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"other\"}}}\n", false);
            assertTrue(otherBookmark.await(5, SECONDS));
            assertTrue(Files.readString(storage.resolve("samples-one.items")).contains("\"metadata\":{\"resourceVersion\":\"old\"}"));
            assertTrue(Files.readString(storage.resolve("samples-one.items")).contains("\"resourceVersion\":\"1\""));
            assertFalse(fixture.runtime.isReady());
        }
    }

    @Test
    void statusRetryAfterSecondsIsBoundedAndBareStatusWatchIsValidated() throws Exception {
        try (final var fixture = new Fixture(null, 1, new TestOperator(), new OperatorConfiguration.Retry(10, 80, false))) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("one", ""), true);
            fixture.client.next().respond(200, snapshot("two", ""), true);
            fixture.client.next().respond(200, "{\"kind\":\"Status\",\"code\":429,\"details\":{\"retryAfterSeconds\":999}}\n", true);
            fixture.client.next();
            fixture.scheduler.retry();
            assertEquals(List.of(80L), fixture.scheduler.delays);
            fixture.client.next().respond(200, "{\"kind\":\"Status\",\"reason\":\"Forbidden\"}\n", true);
            assertNull(fixture.scheduler.retries.poll(100, TimeUnit.MILLISECONDS));
            assertFalse(fixture.runtime.isReady());
        }
    }

    @Test
    void startupUsesFreshValuesAndCollectionVersionWithoutReplayingPersistedAdds(@TempDir final Path storage) throws Exception {
        Files.writeString(storage.resolve("samples-one"), "{\"resourceVersion\":\"old/+\"}");
        Files.writeString(storage.resolve("samples-one.items"), snapshot("old/+",
                object("A", "one", "1") + "," + object("DELETED", "one", "1")));
        final var adds = new CopyOnWriteArrayList<String>();
        final var deletes = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                adds.add(item.metadata().uid() + ":" + item.metadata().resourceVersion());
            }

            @Override
            public void onDelete(final SampleResource item) {
                deletes.add(item.metadata().uid());
            }
        })) {
            fixture.start();
            final var list = fixture.client.next();
            final var otherList = fixture.client.next();
            assertNull(list.request.uri().getRawQuery());
            assertNull(otherList.request.uri().getRawQuery());
            assertTrue(adds.isEmpty());
            assertTrue(deletes.isEmpty());
            assertFalse(fixture.runtime.isReady());
            list.respond(200, "{\"metadata\":{\"resourceVersion\":\"fresh/+\",\"continue\":\"next\"},\"items\":[" + object("A", "one", "2") + "]}", true);
            final var next = fixture.client.next();
            assertEquals("continue=next", next.request.uri().getRawQuery());
            assertTrue(adds.isEmpty());
            assertTrue(deletes.isEmpty());
            next.respond(200, snapshot("fresh/+", object("B", "one", "3")), true);
            final var watch = fixture.client.next();
            assertEquals("watch=true&allowWatchBookmarks=true&resourceVersion=fresh%2F%2B", watch.request.uri().getRawQuery());
            assertEquals(List.of("A:2", "B:3"), adds);
            assertEquals(List.of("DELETED"), deletes);
            assertEquals(List.of("A", "B"), fixture.runtime.items());
            otherList.respond(200, snapshot("two", ""), true);
            fixture.client.next();
            assertTrue(fixture.runtime.isReady());
        }
    }

    @Test
    void missingCorruptLegacyAndMismatchedStateAlwaysUsesFreshList(@TempDir final Path root) throws Exception {
        final var inventories = List.of("missing", "not-json", "{\"items\":[]}",
                snapshot("different", object("DELETED", "one", "1")),
                "{\"items\":[{\"metadata\":{}}]}",
                snapshot("old", object("DELETED", "one", "1") + "," + object("DELETED", "one", "1")),
                "missing", snapshot("old", object("DELETED", "one", "1")),
                "{\"items\":[" + object("DELETED", "one", "1") + "]}");
        for (int i = 0; i < inventories.size(); i++) {
            final var storage = Files.createDirectories(root.resolve(Integer.toString(i)));
            if (i != 6) {
                Files.writeString(storage.resolve("samples-one"), i == 7 ? "not-json" : "{\"resourceVersion\":\"old\"}");
            }
            if (!"missing".equals(inventories.get(i))) {
                Files.writeString(storage.resolve("samples-one.items"), inventories.get(i));
            }
            final var adds = new CopyOnWriteArrayList<String>();
            final var deletes = new CopyOnWriteArrayList<String>();
            try (final var fixture = new Fixture(storage, 1, new TestOperator() {
                @Override
                public void onAdd(final SampleResource item) {
                    adds.add(item.metadata().uid());
                }

                @Override
                public void onDelete(final SampleResource item) {
                    deletes.add(item.metadata().uid());
                }
            })) {
                fixture.start();
                final var list = fixture.client.next();
                assertNull(list.request.uri().getQuery());
                list.respond(200, snapshot("fresh", object("A", "one", "1") + "," + object("B", "one", "2")), true);
                fixture.client.next().respond(200, snapshot("two", ""), true);
                final var first = fixture.client.next();
                final var second = fixture.client.next();
                final var watch = first.request.uri().getPath().contains("/one/") ? first : second;
                assertEquals("watch=true&allowWatchBookmarks=true&resourceVersion=fresh", watch.request.uri().getRawQuery());
                assertEquals(List.of("A", "B"), adds);
                assertEquals(i == 3 || i == 7 || i == 8 ? List.of("DELETED") : List.of(), deletes);
                assertTrue(Files.readString(storage.resolve("samples-one.items")).contains("\"metadata\":{\"resourceVersion\":\"fresh\"}"));
                if (i == 6) {
                    assertFalse(Files.exists(storage.resolve("samples-one")));
                } else {
                    assertEquals(i == 7 ? "not-json" : "{\"resourceVersion\":\"old\"}", Files.readString(storage.resolve("samples-one")), "legacy cursor is ignored, not rewritten");
                }
            }
        }
    }

    @Test
    void oddRetryCapDoesNotSkipTheLastExponentialStep() throws Exception {
        try (final var fixture = new Fixture(null, 1, new TestOperator(), new OperatorConfiguration.Retry(2, 5, false))) {
            fixture.start();
            fixture.client.next().respond(503, "", true);
            fixture.client.next(); // leave the other namespace's initial request pending
            fixture.scheduler.retry();
            fixture.client.next().respond(503, "", true);
            fixture.scheduler.retry();
            fixture.client.next().respond(503, "", true);
            fixture.scheduler.retry();
            fixture.client.next();
            assertEquals(List.of(2L, 4L, 5L), fixture.scheduler.delays);
        }
    }

    @Test
    void stopBeforeAsyncStartupCompletionCannotStartWorkersOrWatches() {
        final var startup = new CompletableFuture<Void>();
        final var stops = new AtomicInteger();
        try (final var fixture = new Fixture(null, 1, new TestOperator() {
            @Override
            public CompletableFuture<?> onStart() {
                return startup;
            }

            @Override
            public void onStop() {
                stops.incrementAndGet();
            }
        })) {
            final var started = fixture.runtime.doStart().toCompletableFuture();
            assertFalse(started.isDone());
            assertFalse(fixture.runtime.isReady());
            fixture.runtime.stop();
            startup.complete(null);
            started.join();
            fixture.runtime.stop();
            assertEquals(1, stops.get());
            assertFalse(fixture.runtime.isReady());
            assertTrue(fixture.client.requests.isEmpty());
            assertTrue(fixture.scheduler.retries.isEmpty());
            assertFalse(fixture.scheduler.isShutdown());
        }
    }

    @Test
    void missingOrBlankListVersionCannotPublishInventoryOrOpenUnversionedWatch(@TempDir final Path root) throws Exception {
        final var metadata = List.of("", "\"metadata\":{},", "\"metadata\":{\"resourceVersion\":\" \"},");
        for (int i = 0; i < metadata.size(); i++) {
            final var storage = Files.createDirectories(root.resolve(Integer.toString(i)));
            final var adds = new CopyOnWriteArrayList<String>();
            try (final var fixture = new Fixture(storage, 1, new TestOperator() {
                @Override
                public void onAdd(final SampleResource item) {
                    adds.add(item.metadata().uid());
                }
            })) {
                fixture.start();
                fixture.client.next().respond(200, "{" + metadata.get(i) + "\"items\":[" + object("A", "one", "1") + "]}", true);
                fixture.client.next().respond(200, snapshot("two", ""), true);
                final var other = fixture.client.next(); // FIFO barrier after the failed list
                assertTrue(other.request.uri().getPath().contains("/two/"));
                assertTrue(adds.isEmpty());
                assertFalse(Files.exists(storage.resolve("samples-one")));
                assertFalse(Files.exists(storage.resolve("samples-one.items")));
                assertFalse(fixture.runtime.isReady());
                assertTrue(fixture.client.requests.isEmpty());
                assertTrue(fixture.scheduler.retries.isEmpty());
            }
        }
    }

    @Test
    void nonemptyOpaqueContinuationIsNotTreatedAsEndOfPagination(@TempDir final Path storage) throws Exception {
        final var adds = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                adds.add(item.metadata().uid());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, "{\"metadata\":{\"resourceVersion\":\"snapshot\",\"continue\":\" \"},\"items\":[" + object("A", "one", "1") + "]}", true);
            final var otherList = fixture.client.next();
            final var next = fixture.client.next();
            assertEquals("continue=+", next.request.uri().getRawQuery());
            assertTrue(adds.isEmpty());
            assertFalse(Files.exists(storage.resolve("samples-one")));
            next.respond(200, snapshot("snapshot", object("B", "one", "2")), true);
            otherList.respond(200, snapshot("two", ""), true);
            fixture.client.next();
            fixture.client.next();
            assertEquals(List.of("A", "B"), adds);
            assertTrue(fixture.runtime.isReady());
        }
    }

    @Test
    void malformedContinuationOnLaterPageCannotPublishPartialSnapshot(@TempDir final Path storage) throws Exception {
        final var adds = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                adds.add(item.metadata().uid());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, "{\"metadata\":{\"resourceVersion\":\"snapshot\",\"continue\":\"next\"},\"items\":[" + object("A", "one", "1") + "]}", true);
            final var otherList = fixture.client.next();
            final var next = fixture.client.next();
            next.respond(200, "{\"metadata\":{\"resourceVersion\":\"snapshot\",\"continue\":42},\"items\":[" + object("B", "one", "2") + "]}", true);
            otherList.respond(200, snapshot("two", ""), true);
            assertTrue(fixture.client.next().request.uri().getPath().contains("/two/"));
            assertTrue(adds.isEmpty());
            assertFalse(Files.exists(storage.resolve("samples-one")));
            assertFalse(Files.exists(storage.resolve("samples-one.items")));
            assertFalse(fixture.runtime.isReady());
            assertTrue(fixture.client.requests.isEmpty());
            assertTrue(fixture.scheduler.retries.isEmpty());
        }
    }

    @Test
    void invalidFreshListCannotMarkReadyOrReplacePersistedCheckpoint(@TempDir final Path storage) throws Exception {
        final var inventory = snapshot("original", object("STALE", "one", "1"));
        Files.writeString(storage.resolve("samples-one"), "{\"resourceVersion\":\"original\"}");
        Files.writeString(storage.resolve("samples-one.items"), inventory);
        final var adds = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                adds.add(item.metadata().uid());
            }
        })) {
            fixture.start();
            final var list = fixture.client.next();
            assertNull(list.request.uri().getRawQuery());
            list.respond(200, "{\"items\":[]}", true);
            fixture.client.next().respond(200, snapshot("two", ""), true);
            assertTrue(fixture.client.next().request.uri().getPath().contains("/two/"));
            assertEquals("{\"resourceVersion\":\"original\"}", Files.readString(storage.resolve("samples-one")));
            assertEquals(inventory, Files.readString(storage.resolve("samples-one.items")));
            assertTrue(adds.isEmpty());
            assertFalse(fixture.runtime.isReady());
            assertTrue(fixture.client.requests.isEmpty());
            assertTrue(fixture.scheduler.retries.isEmpty());
        }
    }

    @Test
    void allPagesAreCollectedBeforeCallbacksAndCheckpoint(@TempDir final Path storage) throws Exception {
        final var adds = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                assertFalse(Files.exists(storage.resolve("samples-one")));
                adds.add(item.metadata().uid());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, "{\"metadata\":{\"resourceVersion\":\"snapshot\",\"continue\":\"next/+\"},\"items\":[" + object("A", "one", "1") + "]}", true);
            fixture.client.next().respond(200, snapshot("two", ""), true);
            final var next = fixture.client.next();
            assertEquals("continue=next%2F%2B", next.request.uri().getRawQuery());
            assertTrue(adds.isEmpty());
            assertFalse(Files.exists(storage.resolve("samples-one")));
            next.respond(200, snapshot("snapshot", object("B", "one", "2")), true);
            fixture.client.next();
            fixture.client.next();
            assertEquals(List.of("A", "B"), adds);
            assertTrue(Files.readString(storage.resolve("samples-one.items")).contains("\"uid\":\"B\""));
        }
    }

    @Test
    void failedLaterPageDoesNotPublishPartialSnapshotOrCheckpoint(@TempDir final Path storage) throws Exception {
        final var adds = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                adds.add(item.metadata().uid());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, "{\"metadata\":{\"resourceVersion\":\"snapshot\",\"continue\":\"next\"},\"items\":[" + object("A", "one", "1") + "]}", true);
            fixture.client.next().respond(200, snapshot("two", ""), true);
            fixture.client.next().respond(503, "secret response", true);
            fixture.client.next(); // other namespace watch remains active
            fixture.scheduler.retry();
            final var retry = fixture.client.next();
            assertNull(retry.request.uri().getQuery());
            assertTrue(adds.isEmpty());
            assertFalse(Files.exists(storage.resolve("samples-one")));
            retry.respond(200, snapshot("new", object("B", "one", "2")), true);
            fixture.client.next();
            assertEquals(List.of("B"), adds);
        }
    }

    @Test
    void permanentListAndHttp200StatusStopOnlyAffectedWatch() throws Exception {
        for (final int code : List.of(401, 403, 404)) {
            for (final boolean inBody : List.of(false, true)) {
                try (final var fixture = new Fixture(null, 1, new TestOperator())) {
                    fixture.start();
                    fixture.client.next().respond(inBody ? 200 : code, "{\"kind\":\"Status\",\"code\":" + code + ",\"message\":\"secret\"}", true);
                    fixture.client.next().respond(200, snapshot("two", ""), true);
                    final var otherWatch = fixture.client.next();
                    assertTrue(otherWatch.request.uri().getPath().contains("/two/"));
                    assertFalse(fixture.runtime.isReady());
                    assertNull(fixture.scheduler.retries.poll(100, TimeUnit.MILLISECONDS));
                }
            }
        }
    }

    @Test
    void permanentInBandStatusStopsWatchAndPreservesOnErrorCallback() throws Exception {
        final var errors = new AtomicInteger();
        try (final var fixture = new Fixture(null, 1, new TestOperator() {
            @Override
            public void onError(final String line) {
                errors.incrementAndGet();
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("one", ""), true);
            fixture.client.next().respond(200, snapshot("two", ""), true);
            fixture.client.next().respond(200, "{\"type\":\"ERROR\",\"object\":{\"kind\":\"Status\",\"code\":403,\"message\":\"secret\"}}\n", true);
            fixture.client.next();
            assertNull(fixture.scheduler.retries.poll(100, TimeUnit.MILLISECONDS));
            assertEquals(1, errors.get());
            assertFalse(fixture.runtime.isReady());
        }
    }

    @Test
    void transientFailuresAndRetryAfterUseCappedDelaysAndPermanentRetryOptIn() throws Exception {
        try (final var fixture = new Fixture(null, 1, new TestOperator(), new OperatorConfiguration.Retry(10, 80, true))) {
            fixture.start();
            fixture.client.next().future.completeExceptionally(new IOException("secret transport details"));
            fixture.client.next().respond(200, snapshot("two", ""), true);
            fixture.client.next();
            fixture.scheduler.retry();
            fixture.client.next().respond(408, "secret", true);
            fixture.scheduler.retry();
            fixture.client.next().respond(429, "secret", true, Map.of("Retry-After", List.of("999999")));
            fixture.scheduler.retry();
            fixture.client.next().respond(503, "secret", true, Map.of("Retry-After", List.of("invalid")));
            fixture.scheduler.retry();
            fixture.client.next().respond(401, "secret", true);
            fixture.scheduler.retry();
            assertEquals(List.of(10L, 20L, 80L, 80L, 80L), fixture.scheduler.delays);
            fixture.client.next().respond(200, snapshot("one", ""), true);
            fixture.client.next();
            assertTrue(fixture.runtime.isReady());
        }
    }

    @Test
    void cancellationAndShutdownDoNotRetry() throws Exception {
        try (final var fixture = new Fixture(null, 1, new TestOperator())) {
            fixture.start();
            fixture.client.next().future.cancel(true);
            final var pending = fixture.client.next();
            assertNull(fixture.scheduler.retries.poll(100, TimeUnit.MILLISECONDS));
            fixture.runtime.stop();
            assertTrue(pending.future.isCancelled());
            assertNull(fixture.scheduler.retries.poll(100, TimeUnit.MILLISECONDS));
            assertFalse(fixture.runtime.isReady());
        }
    }

    @Test
    void freshStartupCallbackFailureLeavesCheckpointUntouchedAndOtherWatchRunning(@TempDir final Path storage) throws Exception {
        final var inventory = snapshot("old", object("A", "one", "1"));
        Files.writeString(storage.resolve("samples-one"), "{\"resourceVersion\":\"old\"}");
        Files.writeString(storage.resolve("samples-one.items"), inventory);
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                throw new IllegalStateException("secret callback details");
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("fresh", object("A", "one", "2")), true);
            final var other = fixture.client.next();
            assertTrue(other.request.uri().getPath().contains("/two/"));
            other.respond(200, snapshot("two", ""), true);
            fixture.client.next();
            assertEquals("{\"resourceVersion\":\"old\"}", Files.readString(storage.resolve("samples-one")));
            assertEquals(inventory, Files.readString(storage.resolve("samples-one.items")));
            assertFalse(fixture.runtime.isReady());
            assertNull(fixture.scheduler.retries.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void reconciliationCallbackFailureCannotAdvanceCheckpoint(@TempDir final Path storage) throws Exception {
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                throw new IllegalStateException("failed");
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("new", object("A", "one", "1")), true);
            fixture.client.next().respond(200, snapshot("two", ""), true);
            final var other = fixture.client.next();
            assertTrue(other.request.uri().getPath().contains("/two/"));
            assertFalse(Files.exists(storage.resolve("samples-one")));
            assertFalse(Files.exists(storage.resolve("samples-one.items")));
            assertFalse(fixture.runtime.isReady());
        }
    }

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
                assertTrue(assertDoesNotThrow(() -> Files.readString(storage.resolve("samples-one.items"))).contains("initial-one"), "fresh checkpoint must follow deletion");
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
            final var inventory = Files.readString(storage.resolve("samples-one.items"));
            assertTrue(inventory.contains("\"metadata\":{\"resourceVersion\":\"fresh\"}"));
            assertTrue(inventory.contains("\"items\":[]"));
            assertTrue(Files.readString(storage.resolve("samples-two.items")).contains("initial-two"));
        }
    }

    @Test
    void restartRetainsKnownItemsForFreshListDeletionReconciliation(@TempDir final Path storage) throws Exception {
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
            final var list = fixture.client.next();
            assertNull(list.request.uri().getRawQuery());
            list.respond(200, snapshot("fresh", ""), true);
            fixture.client.next().respond(200, snapshot("other", ""), true);
            fixture.client.next();
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

    @Test
    void liveReconnectUsesProcessedEventThenBookmarkWithoutRewritingInventoryForBookmark(@TempDir final Path storage) throws Exception {
        final var callbacks = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                callbacks.add(item.metadata().resourceVersion());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("collection", object("A", "one", "999")), true);
            fixture.client.next().respond(200, snapshot("other", ""), true);
            final var first = fixture.client.next();
            final var second = fixture.client.next();
            final var one = first.request.uri().getPath().contains("/one/") ? first : second;
            assertEquals(List.of("999"), callbacks, "current onAdd completes before watch admission");
            one.respond(200, event("A", "one", "event/+"), true);
            fixture.scheduler.retry();
            final var reconnect = fixture.client.next();
            assertTrue(reconnect.request.uri().getRawQuery().endsWith("resourceVersion=event%2F%2B"));
            assertEquals(List.of("999", "event/+"), callbacks);
            final var inventory = Files.readString(storage.resolve("samples-one.items"));
            reconnect.respond(200, "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"mark/+\"}}}\n", true);
            fixture.scheduler.retry();
            assertTrue(fixture.client.next().request.uri().getRawQuery().endsWith("resourceVersion=mark%2F%2B"));
            assertEquals(inventory, Files.readString(storage.resolve("samples-one.items")));
            assertFalse(Files.exists(storage.resolve("samples-one")), "no persistent cursor protocol");
            assertTrue(fixture.runtime.isReady());
        }
    }

    @Test
    void expiredLegacyCursorCannotSubstituteStaleInventoryWhenFreshStartupListFails(@TempDir final Path storage) throws Exception {
        final var old = snapshot("expired", object("stale", "one", "1"));
        Files.writeString(storage.resolve("samples-one"), "{\"resourceVersion\":\"expired\"}");
        Files.writeString(storage.resolve("samples-one.items"), old);
        final var callbacks = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                callbacks.add("add");
            }

            @Override
            public void onDelete(final SampleResource item) {
                callbacks.add("delete");
            }
        })) {
            fixture.start();
            final var list = fixture.client.next();
            final var other = fixture.client.next();
            assertNull(list.request.uri().getRawQuery(), "even an expired persisted cursor must not be validated");
            list.respond(503, "unavailable", true);
            fixture.scheduler.retry();
            final var retry = fixture.client.next();
            assertNull(retry.request.uri().getRawQuery());
            assertEquals(List.of(), callbacks);
            assertFalse(fixture.runtime.isReady());
            assertEquals(old, Files.readString(storage.resolve("samples-one.items")));
            retry.respond(403, "forbidden", true);
            other.respond(200, snapshot("other", ""), true);
            assertTrue(fixture.client.next().request.uri().getPath().contains("/two/")); // ordered barrier
            assertEquals(List.of(), callbacks);
            assertEquals(old, Files.readString(storage.resolve("samples-one.items")));
            assertFalse(fixture.runtime.isReady());
            assertTrue(fixture.client.requests.isEmpty());
            assertTrue(fixture.scheduler.retries.isEmpty());
        }
    }

    @Test
    void inconsistentPaginationWithOldInventoryCannotReconcileOrPersist(@TempDir final Path storage) throws Exception {
        final var old = snapshot("old", object("deleted", "one", "old"));
        Files.writeString(storage.resolve("samples-one.items"), old);
        final var callbacks = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                callbacks.add("add");
            }

            @Override
            public void onDelete(final SampleResource item) {
                callbacks.add("delete");
            }
        })) {
            fixture.start();
            final var list = fixture.client.next();
            final var other = fixture.client.next();
            list.respond(200, "{\"metadata\":{\"resourceVersion\":\"page1\",\"continue\":\"next\"},\"items\":[" + object("A", "one", "1") + "]}", true);
            fixture.client.next().respond(200, snapshot("page2", object("B", "one", "2")), true);
            other.respond(200, snapshot("other", ""), true);
            assertTrue(fixture.client.next().request.uri().getPath().contains("/two/")); // ordered barrier
            assertEquals(List.of(), callbacks);
            assertEquals(old, Files.readString(storage.resolve("samples-one.items")));
            assertFalse(fixture.runtime.isReady());
            assertTrue(fixture.client.requests.isEmpty());
            assertTrue(fixture.scheduler.retries.isEmpty());
        }
    }

    @Test
    void invalidListedIdentitiesCannotPublishAnyCallbacksOrInventory(@TempDir final Path root) throws Exception {
        final var invalid = List.of("null", "{}", object("", "one", "2"),
                object("A", "one", "2"), object("B", "two", "2"));
        for (int i = 0; i < invalid.size(); i++) {
            final var storage = Files.createDirectories(root.resolve(Integer.toString(i)));
            final var old = snapshot("old", object("DELETED", "one", "1"));
            Files.writeString(storage.resolve("samples-one.items"), old);
            final var callbacks = new CopyOnWriteArrayList<String>();
            try (final var fixture = new Fixture(storage, 1, new TestOperator() {
                @Override
                public void onAdd(final SampleResource item) {
                    callbacks.add("add");
                }

                @Override
                public void onDelete(final SampleResource item) {
                    callbacks.add("delete");
                }
            })) {
                fixture.start();
                final var list = fixture.client.next();
                final var other = fixture.client.next();
                list.respond(200, "{\"metadata\":{\"resourceVersion\":\"fresh\",\"continue\":\"next\"},\"items\":[" + object("A", "one", "1") + "]}", true);
                fixture.client.next().respond(200, snapshot("fresh", invalid.get(i)), true);
                other.respond(200, snapshot("other", ""), true);
                assertTrue(fixture.client.next().request.uri().getPath().contains("/two/"));
                assertEquals(List.of(), callbacks, "invalid item must invalidate the entire snapshot: " + i);
                assertEquals(old, Files.readString(storage.resolve("samples-one.items")));
                assertFalse(fixture.runtime.isReady());
                assertTrue(fixture.client.requests.isEmpty());
            }
        }
    }

    @Test
    void inventoryFromAnotherNamespaceCannotDeleteCurrentNamespaceObjects(@TempDir final Path storage) throws Exception {
        // A single-namespace configuration can reuse the same filename after a namespace change.
        Files.writeString(storage.resolve("samples-one.items"), snapshot("old", object("A", "one", "1") + "," + object("B", "two", "1")));
        final var deletes = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onDelete(final SampleResource item) {
                deletes.add(item.metadata().namespace() + ":" + item.metadata().uid());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("fresh", ""), true);
            fixture.client.next().respond(200, snapshot("other", object("B", "two", "2")), true);
            fixture.client.next();
            fixture.client.next();
            assertEquals(List.of(), deletes, "reject the entire inconsistent inventory, not just its last entry");
            assertEquals(List.of("B"), fixture.runtime.items());
            assertTrue(fixture.runtime.isReady());
        }
    }

    @Test
    void downtimeUidReplacementDeletesBeforeAddsAndDeleteFailureStopsReconciliation(@TempDir final Path root) throws Exception {
        for (final boolean fail : List.of(false, true)) {
            final var storage = Files.createDirectories(root.resolve(Boolean.toString(fail)));
            final var old = snapshot("old", object("old-uid", "one", "1"));
            Files.writeString(storage.resolve("samples-one.items"), old);
            final var callbacks = new CopyOnWriteArrayList<String>();
            try (final var fixture = new Fixture(storage, 1, new TestOperator() {
                @Override
                public void onDelete(final SampleResource item) {
                    assertEquals(old, assertDoesNotThrow(() -> Files.readString(storage.resolve("samples-one.items"))));
                    callbacks.add("delete:" + item.metadata().uid());
                    if (fail) {
                        throw new IllegalStateException("delete failed");
                    }
                }

                @Override
                public void onAdd(final SampleResource item) {
                    callbacks.add("add:" + item.metadata().uid());
                }
            })) {
                fixture.start();
                fixture.client.next().respond(200, snapshot("fresh", object("new-uid", "one", "2")), true);
                fixture.client.next().respond(200, snapshot("other", ""), true);
                fixture.client.next();
                if (fail) {
                    assertEquals(List.of("delete:old-uid"), callbacks);
                    assertEquals(old, Files.readString(storage.resolve("samples-one.items")));
                    assertFalse(fixture.runtime.isReady());
                    assertTrue(fixture.client.requests.isEmpty());
                } else {
                    fixture.client.next();
                    assertEquals(List.of("delete:old-uid", "add:new-uid"), callbacks);
                    assertEquals(List.of("new-uid"), fixture.runtime.items());
                    assertTrue(fixture.runtime.isReady());
                }
            }
        }
    }

    @Test
    void inventoryIoFailureAfterCallbacksDoesNotRollBackLiveCursorOrReadiness(@TempDir final Path storage) throws Exception {
        // A nonempty directory cannot be replaced by the inventory's atomic file move.
        final var inventory = Files.createDirectories(storage.resolve("samples-one.items"));
        Files.writeString(inventory.resolve("blocker"), "keep");
        final var adds = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public void onAdd(final SampleResource item) {
                adds.add(item.metadata().resourceVersion());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("fresh", object("A", "one", "1")), true);
            fixture.client.next().respond(200, snapshot("other", ""), true);
            final var first = fixture.client.next();
            final var second = fixture.client.next();
            final var one = first.request.uri().getPath().contains("/one/") ? first : second;
            one.respond(200, event("A", "one", "processed/+"), true);
            fixture.scheduler.retry();
            assertTrue(fixture.client.next().request.uri().getRawQuery().endsWith("resourceVersion=processed%2F%2B"));
            assertEquals(List.of("1", "processed/+"), adds);
            assertTrue(fixture.runtime.isReady());
            assertEquals("keep", Files.readString(inventory.resolve("blocker")));
            try (final var files = Files.list(storage)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
            }
        }
    }

    @Test
    void unknownMalformedAndBlankVersionEventsCannotAdvanceLiveCursor() throws Exception {
        try (final var fixture = new Fixture(null, 1, new TestOperator())) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("fresh", ""), true);
            fixture.client.next().respond(200, snapshot("other", ""), true);
            final var first = fixture.client.next();
            final var second = fixture.client.next();
            final var one = first.request.uri().getPath().contains("/one/") ? first : second;
            one.respond(200, "not-json\n" +
                    "{\"type\":\"UNKNOWN\",\"object\":{\"metadata\":{\"resourceVersion\":\"unsafe\"}}}\n" +
                    "{\"type\":\"ADDED\",\"object\":null}\n" + event("A", "one", " ") +
                    "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"\"}}}\n", true);
            fixture.scheduler.retry();
            assertTrue(fixture.client.next().request.uri().getRawQuery().endsWith("resourceVersion=fresh"));
            assertTrue(fixture.runtime.isReady());
        }
    }

    @Test
    void malformedBookmarkVersionStopsOnlyAffectedWatchWithoutSavingUnsafeCursor(@TempDir final Path storage) throws Exception {
        try (final var fixture = new Fixture(storage, 1, new TestOperator())) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("fresh", ""), true);
            fixture.client.next().respond(200, snapshot("other", ""), true);
            final var first = fixture.client.next();
            final var second = fixture.client.next();
            final var one = first.request.uri().getPath().contains("/one/") ? first : second;
            final var two = one == first ? second : first;
            final var inventory = Files.readString(storage.resolve("samples-one.items"));
            one.respond(200, "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":42}}}\n" +
                    "{\"type\":\"BOOKMARK\",\"object\":{\"metadata\":{\"resourceVersion\":\"unsafe\"}}}\n", true);
            two.respond(200, "", true);
            fixture.scheduler.retry();
            assertTrue(fixture.client.next().request.uri().getPath().contains("/two/"));
            assertEquals(inventory, Files.readString(storage.resolve("samples-one.items")));
            assertFalse(fixture.runtime.isReady());
            assertTrue(fixture.scheduler.retries.isEmpty());
        }
    }

    @Test
    void clusterScopedInventoryAcceptsAbsentNamespaceAndReconcilesDowntimeDeletion(@TempDir final Path storage) throws Exception {
        Files.writeString(storage.resolve("samples.items"), snapshot("old", "{\"metadata\":{\"uid\":\"old\"}}"));
        final var callbacks = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public boolean namespaced() {
                return false;
            }

            @Override
            public void onDelete(final SampleResource item) {
                callbacks.add("delete:" + item.metadata().uid());
            }

            @Override
            public void onAdd(final SampleResource item) {
                callbacks.add("add:" + item.metadata().uid());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("fresh", "{\"metadata\":{\"uid\":\"new\"}}"), true);
            final var watch = fixture.client.next();
            assertEquals("/apis/test.io/v1/samples", watch.request.uri().getPath());
            assertTrue(watch.request.uri().getRawQuery().endsWith("resourceVersion=fresh"));
            assertEquals(List.of("delete:old", "add:new"), callbacks);
            assertTrue(fixture.runtime.isReady());
        }
    }

    @Test
    void globalCollectionWatchCanReconcileResourcesAcrossNamespaces(@TempDir final Path storage) throws Exception {
        Files.writeString(storage.resolve("samples.items"), snapshot("old", object("old", "one", "1")));
        final var callbacks = new CopyOnWriteArrayList<String>();
        try (final var fixture = new Fixture(storage, 1, new TestOperator() {
            @Override
            public boolean namespaced() {
                return false;
            }

            @Override
            public void onDelete(final SampleResource item) {
                callbacks.add("delete:" + item.metadata().namespace());
            }

            @Override
            public void onAdd(final SampleResource item) {
                callbacks.add("add:" + item.metadata().namespace());
            }
        })) {
            fixture.start();
            fixture.client.next().respond(200, snapshot("fresh", object("new", "two", "2")), true);
            final var watch = fixture.client.next();
            assertEquals("/apis/test.io/v1/samples", watch.request.uri().getPath());
            assertEquals(List.of("delete:one", "add:two"), callbacks);
            assertTrue(fixture.runtime.isReady());
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
            this(storage, eventThreads, operator, new OperatorConfiguration.Retry(100, 5_000, false));
        }

        private Fixture(final Path storage, final int eventThreads, final TestOperator operator, final OperatorConfiguration.Retry retry) {
            runtime = new InspectableRuntime(new OperatorConfiguration(true, null, true, eventThreads, -1, false,
                    storage == null ? null : storage.toString(), retry), client, scheduler, mapper, operator);
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

        private void legacyEvent(final String line) {
            queue.add(line);
        }

        private List<String> items() {
            return state.items().stream().map(it -> it.metadata().uid()).toList();
        }
    }

    private static class ManualScheduler extends ScheduledThreadPoolExecutor {
        private final BlockingQueue<Runnable> retries = new LinkedBlockingQueue<>();
        private final List<Long> delays = new CopyOnWriteArrayList<>();

        private ManualScheduler() {
            super(1);
        }

        @Override
        public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
            delays.add(unit.toMillis(delay));
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
            respond(status, body, complete, Map.of());
        }

        private void respond(final int status, final String body, final boolean complete, final Map<String, List<String>> headers) {
            subscriber = handler.apply(new HttpResponse.ResponseInfo() {
                @Override public int statusCode() { return status; }
                @Override public HttpHeaders headers() { return HttpHeaders.of(headers, (a, b) -> true); }
                @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
            });
            subscriber.getBody().whenComplete((value, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                } else {
                    future.complete(new Response<>(request, status, value, HttpHeaders.of(headers, (a, b) -> true)));
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

    private record Response<T>(HttpRequest request, int statusCode, T body, HttpHeaders headers) implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
}
