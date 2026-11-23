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
package io.yupiik.kubernetes.operator.base.impl;

import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.kubernetes.operator.base.spi.Operator;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class OperatorRuntime<T extends ObjectLike> extends SimpleController<T> implements Awaiter {
    private static final int DRAIN_BATCH_SIZE = 32;
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final OperatorConfiguration configuration;
    private final KubernetesClient kubernetes;
    private final ScheduledExecutorService threads;
    private final JsonMapper jsonMapper;
    private final List<Watch> watches;
    private final ReentrantLock lifecycle = new ReentrantLock();
    private final CountDownLatch awaiter = new CountDownLatch(1);
    private volatile boolean closing;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();

    public OperatorRuntime(final OperatorConfiguration configuration,
                           final KubernetesClient kubernetes,
                           final ScheduledExecutorService threads,
                           final JsonMapper jsonMapper,
                           final Operator<T> spec) {
        super(threads, jsonMapper, configuration.eventThreadCount(), spec);
        this.configuration = configuration;
        this.kubernetes = kubernetes;
        this.threads = threads;
        this.jsonMapper = jsonMapper;
        final var namespaces = spec.namespaced() ? spec.namespaces() : List.of("");
        this.watches = namespaces.stream().distinct().map(namespace -> {
            final var uri = URI.create("https://kubernetes.api/apis/" + spec.apiVersion() + "/" +
                    (spec.namespaced() ? "namespaces/" + namespace + "/" : "") + spec.pluralName());
            final var storage = configuration.storage() == null ? null :
                    Path.of(configuration.storage()).resolve(spec.pluralName() +
                            (namespaces.size() > 1 ? "-" + namespace : ""));
            return new Watch(uri, namespace, storage);
        }).toList();
    }

    public CompletionStage<?> doStart() {
        if (!started.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        return operator.onStart().thenRun(() -> {
            if (!closing) {
                init();
                watches.forEach(watch -> {
                    loadKnownItems(watch);
                    connect(watch, true);
                });
            }
        });
    }

    @Override
    public void stop() {
        doStop();
    }

    public void doStop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        final List<Runnable> cancellations;
        lifecycle.lock();
        try {
            closing = true;
            cancellations = watches.stream().<Runnable>map(watch -> {
                final var retry = watch.retry;
                final var subscription = watch.subscription;
                final var request = watch.request;
                return () -> {
                    if (retry != null) {
                        retry.cancel(false);
                    }
                    if (subscription != null) {
                        subscription.cancel();
                    }
                    if (request != null) {
                        request.cancel(true);
                    }
                };
            }).toList();
        } finally {
            lifecycle.unlock();
        }
        // Cancellation can invoke HTTP callbacks: never call it under the lifecycle lock.
        cancellations.forEach(Runnable::run);
        try {
            super.stop(); // bounded drain, then interrupt only our own workers
            operator.onStop();
        } finally {
            awaiter.countDown();
        }
    }

    /** Initial inventory has been delivered and no watch has failed terminally. Transient reconnects retain readiness. */
    public boolean isReady() {
        return started.get() && !closing && watches.stream().allMatch(watch -> watch.ready && !watch.terminal);
    }

    private void connect(final Watch watch, final boolean list) {
        lifecycle.lock();
        try {
            if (closing || watch.terminal) {
                return;
            }
            final var finished = new AtomicBoolean();
            final var uri = list ? watch.uri :
                    query(watch.uri, "watch=true" +
                            (configuration.useBookmarks() ? "&allowWatchBookmarks=true" : "") + resume(watch));
            if (list) {
                listPage(watch, uri, new ArrayList<>(), null, new HashSet<>(), finished);
                return;
            } else {
                watch.request = kubernetes.sendAsync(request(uri), response -> {
                    if (response.statusCode() != 200) {
                        return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.discarding(), body -> {
                            checkStatus(response.statusCode(), response.headers());
                            return (Void) null;
                        });
                    }
                    return HttpResponse.BodySubscribers.fromLineSubscriber(new Flow.Subscriber<String>() {
                        private Flow.Subscription subscription;

                        @Override
                        public void onSubscribe(final Flow.Subscription subscription) {
                            lifecycle.lock();
                            try {
                                this.subscription = subscription;
                                watch.subscription = subscription;
                                if (closing || watch.terminal) {
                                    subscription.cancel();
                                } else {
                                    subscription.request(1);
                                }
                            } finally {
                                lifecycle.unlock();
                            }
                        }

                        @Override
                        public void onNext(final String line) {
                            try {
                                if (line.isBlank()) {
                                    return;
                                }
                                if (finished.get() || watch.terminal) {
                                    return;
                                }
                                final var raw = jsonMapper.fromString(Map.class, line);
                                final var event = jsonMapper.fromString(Event.class, line);
                                if ("ERROR".equals(event.type()) || "Status".equals(raw.get("kind"))) {
                                    final var failure = statusError("Status".equals(raw.get("kind")) ? raw : event.object());
                                    if ("ERROR".equals(event.type()) && failure.status != 410) {
                                        // Preserve the SPI callback, but not SimpleController's raw-body diagnostic.
                                        dispatch(watch, () -> operator.onError(line));
                                    }
                                    finish(watch, finished, failure, false);
                                    subscription.cancel();
                                    return;
                                }
                                if (event.type() == null || event.object() == null ||
                                        !List.of("ADDED", "MODIFIED", "DELETED", "BOOKMARK").contains(event.type())) {
                                    diagnostic(watch, "watch", "invalid event ignored", 0, 0);
                                    return;
                                }
                                watch.delay = configuration.retry().initialDelay();
                                watch.failures = 0;
                                dispatch(watch, () -> {
                                    handleEvent(line);
                                    trackWatchEvent(watch, event);
                                    final var metadata = event.object().get("metadata");
                                    if (metadata instanceof Map<?, ?> map && map.get("resourceVersion") instanceof String version && !version.isBlank()) {
                                        watch.resourceVersion = version; // opaque, and advanced only after the callback
                                    }
                                    if (!"BOOKMARK".equals(event.type())) {
                                        persistInventory(watch);
                                    }
                                });
                            } catch (final RuntimeException e) {
                                diagnostic(watch, "watch", "invalid event ignored", 0, 0);
                            } finally {
                                if (!finished.get() && !watch.terminal && !closing) {
                                    subscription.request(1);
                                }
                            }
                        }

                        @Override
                        public void onError(final Throwable error) {
                            // The response future completes exceptionally too; reconnect only once.
                        }

                        @Override
                        public void onComplete() {
                            // The response future completes on normal EOF too.
                        }
                    });
                });
            }
            watch.request.whenComplete((ignored, error) -> finish(watch, finished, error, false));
        } catch (final RuntimeException e) {
            finish(watch, new AtomicBoolean(), e, list);
        } finally {
            lifecycle.unlock();
        }
    }

    private void listPage(final Watch watch, final URI uri,
                          final List<Map<String, Object>> items, final String version,
                          final HashSet<String> continuations, final AtomicBoolean finished) {
        if (closing || watch.terminal) {
            return;
        }
        final var future = kubernetes.sendAsync(request(uri), ofString());
        watch.request = future;
        future.thenAccept(response -> {
            checkStatus(response.statusCode(), response.headers());
            final var raw = jsonMapper.fromString(Map.class, response.body());
            if ("Status".equals(raw.get("kind"))) {
                throw statusError(raw);
            }
            if (!(raw.get("items") instanceof List<?>)) {
                throw new IllegalArgumentException("Invalid list");
            }
            final var data = jsonMapper.fromString(EventList.class, response.body());
            final var pageVersion = data.metadata() == null ? null : data.metadata().resourceVersion();
            if (pageVersion == null || pageVersion.isBlank()) {
                throw new IllegalArgumentException("Missing list snapshot version");
            }
            final var metadata = raw.get("metadata");
            final var next = metadata instanceof Map<?, ?> map ? map.get("continue") : null;
            if (next != null && !(next instanceof String)) {
                throw new IllegalArgumentException("Invalid list continuation");
            }
            if (version != null && !version.equals(pageVersion)) {
                throw new IllegalArgumentException("Inconsistent list snapshot");
            }
            items.addAll(data.items());
            if (next instanceof String token && !token.isEmpty()) {
                if (!continuations.add(token)) {
                    throw new IllegalArgumentException("Invalid list continuation");
                }
                lifecycle.lock();
                try {
                    listPage(watch, query(watch.uri, "continue=" + URLEncoder.encode(token, StandardCharsets.UTF_8)),
                            items, pageVersion, continuations, finished);
                } finally {
                    lifecycle.unlock();
                }
            } else {
                validateItems(watch, items);
                dispatch(watch, () -> reconcile(watch, new EventList(new ListMetadata(pageVersion), items)));
                dispatch(watch, () -> {
                    watch.ready = true;
                    connect(watch, false);
                });
            }
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                finish(watch, finished, error, true);
            }
        });
    }

    private void finish(final Watch watch, final AtomicBoolean finished, final Throwable error, final boolean list) {
        if (!finished.compareAndSet(false, true) || closing || watch.terminal) {
            return;
        }
        var cause = error;
        boolean transport = cause instanceof IOException;
        while (cause != null && cause.getCause() != null &&
                (cause instanceof CompletionException || cause instanceof IOException)) {
            cause = cause.getCause();
            transport |= cause instanceof IOException;
        }
        final boolean expired = cause instanceof ClientHttpException http && http.status == 410;
        if (cause instanceof CancellationException) {
            return;
        }
        final int status = cause instanceof ClientHttpException http ? http.status : 0;
        final long retryAfter = cause instanceof ClientHttpException http ? http.retryAfter : 0;
        final boolean retryable = cause == null || status == 0 && transport || expired ||
                status == 408 || status == 429 || status >= 500 && status <= 599;
        final var category = cause == null ? "stream ended" : status == 0 ?
                (transport ? "transport failure" : "invalid response") : "API failure";
        dispatch(watch, () -> {
            if (!retryable && !configuration.retry().retryPermanentErrors()) {
                terminate(watch, list ? "list" : "watch", category, status);
                return;
            }
            if (expired) {
                watch.resourceVersion = null;
            }
            schedule(watch, list || expired, retryAfter, category, status);
        });
    }

    // Keep each watch in stream order even when different namespaces use multiple event workers.
    private void dispatch(final Watch watch, final Runnable task) {
        lifecycle.lock();
        try {
            if (closing || watch.terminal) {
                return;
            }
            watch.pending.add(task);
            if (watch.running.compareAndSet(false, true)) {
                enqueue(() -> drain(watch));
            }
        } finally {
            lifecycle.unlock();
        }
    }

    private void drain(final Watch watch) {
        try {
            for (int i = 0; i < DRAIN_BATCH_SIZE; i++) {
                if (watch.terminal) {
                    watch.pending.clear();
                    break;
                }
                final var task = watch.pending.poll();
                if (task == null) {
                    break;
                }
                runEvent(watch, task);
                if (Thread.currentThread().isInterrupted()) {
                    watch.pending.clear();
                    return;
                }
            }
        } finally {
            watch.running.set(false);
            if (!watch.pending.isEmpty() && watch.running.compareAndSet(false, true)) {
                // This is already accepted work, including when stop has closed admission.
                enqueueContinuation(() -> drain(watch));
            }
        }
    }

    private void runEvent(final Watch watch, final Runnable task) {
        try {
            task.run();
        } catch (final RuntimeException e) {
            // Do not advance past failed callbacks. Restart reconciles a fresh API snapshot.
            terminate(watch, "callback", "handler failed; restart required", 0);
        }
    }

    private String uid(final Map<String, Object> item) {
        return (String) ((Map<?, ?>) item.get("metadata")).get("uid");
    }

    private void handleWatchEvent(final Watch watch, final Event event) {
        handleEvent(jsonMapper.toString(event));
        trackWatchEvent(watch, event);
    }

    private void trackWatchEvent(final Watch watch, final Event event) {
        switch (event.type()) {
            case "ADDED", "MODIFIED" -> watch.known.put(uid(event.object()), event.object());
            case "DELETED" -> watch.known.remove(uid(event.object()));
            default -> { }
        }
    }

    private void reconcile(final Watch watch, final EventList snapshot) {
        final var items = snapshot.items();
        final var present = new HashSet<String>();
        items.forEach(item -> present.add(uid(item)));
        // Compare only this watch's inventory, never clear another namespace's controller state.
        final var missing = watch.known.entrySet().stream()
                .filter(entry -> !present.contains(entry.getKey())).map(Map.Entry::getValue).toList();
        final var reconciliation = new ArrayList<Runnable>();
        missing.forEach(item -> reconciliation.add(() -> handleWatchEvent(watch, new Event("DELETED", item))));
        items.forEach(item -> reconciliation.add(() -> handleWatchEvent(watch, new Event("ADDED", item))));
        reconciliation.add(() -> {
            watch.resourceVersion = snapshot.metadata().resourceVersion(); // collection version, never an item version
            persistInventory(watch);
        });
        // Expand this accepted snapshot in place, ahead of the next watch connection.
        // Each callback gets a separate batch slot, so a large relist also yields fairly.
        for (int i = reconciliation.size() - 1; i >= 0; i--) {
            watch.pending.addFirst(reconciliation.get(i));
        }
    }

    private void loadKnownItems(final Watch watch) {
        watch.delay = configuration.retry().initialDelay();
        if (watch.inventory != null && Files.exists(watch.inventory)) {
            try {
                final var snapshot = jsonMapper.fromString(EventList.class, Files.readString(watch.inventory));
                validateItems(watch, snapshot.items());
                // Historical objects identify downtime deletions only, never startup onAdd input.
                snapshot.items().forEach(item -> watch.known.put(uid(item), item));
            } catch (final IOException | RuntimeException e) {
                diagnostic(watch, "storage", "unusable inventory; downtime deletions unavailable", 0, 0);
            }
        }
    }

    private void validateItems(final Watch watch, final List<Map<String, Object>> items) {
        if (items == null) {
            throw new IllegalArgumentException("Invalid inventory");
        }
        final var ids = new HashSet<String>();
        for (final var item : items) {
            final var id = uid(item);
            if (id == null || id.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException("Invalid inventory identity");
            }
            final var namespace = ((Map<?, ?>) item.get("metadata")).get("namespace");
            // Global collection watches can also list namespaced resources across all namespaces.
            if (!watch.namespace.isEmpty() && !watch.namespace.equals(namespace)) {
                throw new IllegalArgumentException("Invalid inventory namespace");
            }
        }
    }

    private void schedule(final Watch watch, final boolean list, final long retryAfter, final String category, final int status) {
        lifecycle.lock();
        try {
            if (!closing && !watch.terminal) {
                final var delay = Math.min(configuration.retry().maxDelay(), Math.max(watch.delay, retryAfter));
                diagnostic(watch, list ? "list" : "watch", category, status, delay);
                watch.retry = threads.schedule(() -> connect(watch, list), delay, MILLISECONDS);
                watch.delay = watch.delay > configuration.retry().maxDelay() / 2 ?
                        configuration.retry().maxDelay() : watch.delay * 2;
            }
        } finally {
            lifecycle.unlock();
        }
    }

    private void terminate(final Watch watch, final String phase, final String category, final int status) {
        final ScheduledFuture<?> retry;
        final Flow.Subscription subscription;
        final CompletableFuture<?> request;
        lifecycle.lock();
        try {
            watch.terminal = true;
            watch.ready = false;
            retry = watch.retry;
            subscription = watch.subscription;
            request = watch.request;
        } finally {
            lifecycle.unlock();
        }
        if (retry != null) {
            retry.cancel(false);
        }
        if (subscription != null) {
            subscription.cancel();
        }
        if (request != null) {
            request.cancel(true);
        }
        // Terminal errors are always reported, regardless of retry diagnostic sampling.
        watch.failures = 0;
        diagnostic(watch, phase, category + "; affected watch stopped", status, 0);
    }

    private void diagnostic(final Watch watch, final String phase, final String category, final int status, final long delay) {
        if (closing) {
            return;
        }
        final var count = ++watch.failures;
        if (count <= 4 || (count & (count - 1)) == 0 || count % 64 == 0) {
            // Never log response bodies, exception messages, credentials, URLs or resource contents.
            logger.warning("Kubernetes " + phase + " [operator=" + operator.getClass().getName() +
                    ", watch=" + watches.indexOf(watch) + "]: " + category +
                    (status == 0 ? "" : " (status=" + status + ")") +
                    (delay == 0 ? "" : "; retry in " + delay + "ms") + " [attempt=" + count + "]");
        }
    }

    private HttpRequest request(final URI uri) {
        return HttpRequest.newBuilder(uri).GET().header("accept", "application/json").build();
    }

    private URI query(final URI uri, final String query) {
        return URI.create(uri.toASCIIString() + "?" + query);
    }

    private String resume(final Watch watch) {
        return watch.resourceVersion == null ? "" :
                "&resourceVersion=" + URLEncoder.encode(watch.resourceVersion, StandardCharsets.UTF_8);
    }

    private void checkStatus(final int status, final HttpHeaders headers) {
        if (status != 200) {
            throw new ClientHttpException(status, (status == 429 || status == 503) ?
                    retryAfter(headers.firstValue("Retry-After").orElse("")) : 0);
        }
    }

    private long retryAfter(final String value) {
        try {
            final var seconds = Long.parseLong(value);
            return seconds <= 0 ? 0 : seconds > configuration.retry().maxDelay() / 1_000 ?
                    configuration.retry().maxDelay() : seconds * 1_000;
        } catch (final NumberFormatException ignored) {
            try {
                final var instant = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Math.max(0, Math.min(configuration.retry().maxDelay(), instant.toEpochMilli() - System.currentTimeMillis()));
            } catch (final DateTimeParseException | ArithmeticException invalid) {
                return 0;
            }
        }
    }

    private ClientHttpException statusError(final Map<?, ?> status) {
        final var code = status == null ? null : status.get("code");
        final var reason = status == null ? null : status.get("reason");
        final int value = code instanceof Number number && number.intValue() >= 400 && number.intValue() <= 599 ?
                number.intValue() : "Expired".equals(reason) || "Gone".equals(reason) ? 410 :
                "Unauthorized".equals(reason) ? 401 : "Forbidden".equals(reason) ? 403 :
                "NotFound".equals(reason) ? 404 : "TooManyRequests".equals(reason) ? 429 :
                "ServiceUnavailable".equals(reason) ? 503 : "InternalError".equals(reason) ? 500 :
                "Timeout".equals(reason) || "ServerTimeout".equals(reason) ? 504 : 0;
        final var details = status == null ? null : status.get("details");
        final var seconds = details instanceof Map<?, ?> map ? map.get("retryAfterSeconds") : null;
        final var delay = (value == 429 || value == 503) && seconds instanceof Number number ?
                retryAfter(Long.toString(number.longValue())) : 0;
        return new ClientHttpException(value, delay);
    }

    private void persistInventory(final Watch watch) {
        if (watch.inventory != null) {
            try {
                Files.createDirectories(watch.inventory.getParent());
                // Keep the existing .items shape for upgrades. Its metadata is informational only;
                // startup always lists current API state, and the live cursor stays in memory.
                persist(watch.inventory, jsonMapper.toString(new EventList(
                        new ListMetadata(watch.resourceVersion), List.copyOf(watch.known.values()))));
            } catch (final IOException e) {
                diagnostic(watch, "storage", "cannot persist inventory", 0, 0);
            }
        }
    }

    private void persist(final Path target, final String content) throws IOException {
        final var temporary = Files.createTempFile(target.getParent(), target.getFileName() + "-", ".tmp");
        try {
            Files.writeString(temporary, content);
            try {
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void await() {
        if (configuration.await()) {
            try {
                awaiter.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class Watch {
        private final URI uri;
        private final String namespace;
        private final Path inventory;
        private final Map<String, Map<String, Object>> known = new LinkedHashMap<>();
        private final ConcurrentLinkedDeque<Runnable> pending = new ConcurrentLinkedDeque<>();
        private final AtomicBoolean running = new AtomicBoolean();
        private volatile String resourceVersion;
        private volatile long delay = 100;
        private volatile boolean ready;
        private volatile boolean terminal;
        private long failures;
        private Flow.Subscription subscription;
        private CompletableFuture<?> request;
        private ScheduledFuture<?> retry;

        private Watch(final URI uri, final String namespace, final Path storage) {
            this.uri = uri;
            this.namespace = namespace;
            this.inventory = storage == null ? null : storage.resolveSibling(storage.getFileName() + ".items");
        }
    }

    private static class ClientHttpException extends RuntimeException {
        private final int status;
        private final long retryAfter;

        private ClientHttpException(final int status, final long retryAfter) {
            super("Kubernetes HTTP status " + status);
            this.status = status;
            this.retryAfter = retryAfter;
        }
    }

    @JsonModel
    public record OperatorState(String resourceVersion) { // Legacy public model; cursor files are no longer read or written.
    }

    @JsonModel
    public record ListMetadata(String resourceVersion) {
    }

    @JsonModel
    public record EventList(ListMetadata metadata, List<Map<String, Object>> items) {
    }
}
