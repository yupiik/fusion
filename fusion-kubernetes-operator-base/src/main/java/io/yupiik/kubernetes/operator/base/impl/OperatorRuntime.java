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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import static java.util.logging.Level.WARNING;

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
            return new Watch(uri, storage);
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
                    watch.resourceVersion = loadResourceVersion(watch);
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

    private void connect(final Watch watch, final boolean list) {
        lifecycle.lock();
        try {
            if (closing) {
                return;
            }
            final var finished = new AtomicBoolean();
            final var uri = list ? (watch.resourceVersion == null ? watch.uri : query(watch.uri, resume(watch).substring(1))) :
                    query(watch.uri, "watch=true" +
                            (configuration.useBookmarks() ? "&allowWatchBookmarks=true" : "") + resume(watch));
            final CompletableFuture<?> completion;
            if (list) {
                final var responseFuture = kubernetes.sendAsync(request(uri), ofString());
                watch.request = responseFuture;
                completion = responseFuture.thenAccept(response -> {
                            checkStatus(response.statusCode());
                            final var data = jsonMapper.fromString(EventList.class, response.body());
                            // A resumed list only validates the token. A fresh list establishes the snapshot.
                            if (watch.resourceVersion == null) {
                                dispatch(watch, () -> reconcile(watch, data));
                            }
                            dispatch(watch, () -> connect(watch, false));
                        });
            } else {
                watch.request = kubernetes.sendAsync(request(uri), response -> {
                    if (response.statusCode() != 200) {
                        return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), body -> {
                            checkStatus(response.statusCode());
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
                                if (closing) {
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
                                final var event = jsonMapper.fromString(Event.class, line);
                                if ("ERROR".equals(event.type()) && event.object() != null &&
                                        event.object().get("code") instanceof Number code && code.intValue() == 410) {
                                    subscription.cancel();
                                    finish(watch, finished, new ClientHttpException(410), false);
                                    return;
                                }
                                watch.delay = 100;
                                dispatch(watch, () -> {
                                    trackWatchEvent(watch, event);
                                    handleEvent(line);
                                    if (event.object() != null && ("BOOKMARK".equals(event.type()) ||
                                            (!configuration.useBookmarks() && List.of("ADDED", "MODIFIED", "DELETED").contains(event.type())))) {
                                        final var metadata = event.object().get("metadata");
                                        if (metadata instanceof Map<?, ?> map) {
                                            checkpoint(watch, (String) map.get("resourceVersion"));
                                        }
                                    }
                                });
                            } catch (final RuntimeException e) {
                                logger.log(WARNING, "Invalid watch event", e);
                            } finally {
                                subscription.request(1);
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
                completion = watch.request;
            }
            completion.whenComplete((ignored, error) -> {
                if (error != null || !list) {
                    finish(watch, finished, error, list);
                }
            });
        } catch (final RuntimeException e) {
            logger.log(WARNING, "Can't connect to Kubernetes; retrying with bounded backoff", e);
            schedule(watch, list);
        } finally {
            lifecycle.unlock();
        }
    }

    private void finish(final Watch watch, final AtomicBoolean finished, final Throwable error, final boolean list) {
        if (!finished.compareAndSet(false, true) || closing) {
            return;
        }
        var cause = error;
        while (cause != null && cause.getCause() != null &&
                (cause instanceof CompletionException || cause instanceof IOException)) {
            cause = cause.getCause();
        }
        final boolean expired = cause instanceof ClientHttpException http && http.status == 410;
        dispatch(watch, () -> {
            if (expired) {
                watch.resourceVersion = null;
                if (watch.storage != null) {
                    try {
                        Files.deleteIfExists(watch.storage);
                    } catch (final IOException e) {
                        logger.log(WARNING, "Can't remove expired resource version", e);
                    }
                }
            }
            schedule(watch, list || expired);
        });
    }

    // Keep each watch in stream order even when different namespaces use multiple event workers.
    private void dispatch(final Watch watch, final Runnable task) {
        lifecycle.lock();
        try {
            if (closing) {
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
                final var task = watch.pending.poll();
                if (task == null) {
                    break;
                }
                runEvent(task);
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

    private void runEvent(final Runnable task) {
        try {
            task.run();
        } catch (final RuntimeException e) {
            logger.log(WARNING, "Event handler failed; continuing with the next event", e);
        }
    }

    private String uid(final Map<String, Object> item) {
        return (String) ((Map<?, ?>) item.get("metadata")).get("uid");
    }

    private void handleWatchEvent(final Watch watch, final Event event) {
        trackWatchEvent(watch, event);
        handleEvent(jsonMapper.toString(event));
    }

    private void trackWatchEvent(final Watch watch, final Event event) {
        if (event.object() != null && event.type() != null) {
            switch (event.type()) {
                case "ADDED", "MODIFIED" -> watch.known.put(uid(event.object()), event.object());
                case "DELETED" -> watch.known.remove(uid(event.object()));
                default -> { }
            }
        }
    }

    private void reconcile(final Watch watch, final EventList snapshot) {
        final var items = snapshot.items() == null ? List.<Map<String, Object>>of() : snapshot.items();
        final var present = new HashSet<String>();
        items.forEach(item -> present.add(uid(item)));
        // Compare only this watch's inventory, never clear another namespace's controller state.
        final var missing = watch.known.entrySet().stream()
                .filter(entry -> !present.contains(entry.getKey())).map(Map.Entry::getValue).toList();
        final var reconciliation = new ArrayList<Runnable>();
        missing.forEach(item -> reconciliation.add(() -> handleWatchEvent(watch, new Event("DELETED", item))));
        items.forEach(item -> reconciliation.add(() -> handleWatchEvent(watch, new Event("ADDED", item))));
        if (snapshot.metadata() != null) {
            reconciliation.add(() -> checkpoint(watch, snapshot.metadata().resourceVersion()));
        }
        // Expand this accepted snapshot in place, ahead of the next watch connection.
        // Each callback gets a separate batch slot, so a large relist also yields fairly.
        for (int i = reconciliation.size() - 1; i >= 0; i--) {
            watch.pending.addFirst(reconciliation.get(i));
        }
    }

    private void loadKnownItems(final Watch watch) {
        if (watch.inventory != null && Files.exists(watch.inventory)) {
            try {
                final var snapshot = jsonMapper.fromString(EventList.class, Files.readString(watch.inventory));
                if (snapshot.items() != null) {
                    snapshot.items().forEach(item -> dispatch(watch, () -> handleWatchEvent(watch, new Event("ADDED", item))));
                }
            } catch (final IOException | RuntimeException e) {
                logger.log(WARNING, "Can't load known resources", e);
            }
        }
    }

    private void schedule(final Watch watch, final boolean list) {
        lifecycle.lock();
        try {
            if (!closing) {
                watch.retry = threads.schedule(() -> connect(watch, list), watch.delay, MILLISECONDS);
                watch.delay = Math.min(5_000, watch.delay * 2);
            }
        } finally {
            lifecycle.unlock();
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

    private void checkStatus(final int status) {
        if (status != 200) {
            throw new ClientHttpException(status);
        }
    }

    private String loadResourceVersion(final Watch watch) {
        if (watch.storage != null && Files.exists(watch.storage)) {
            try {
                return jsonMapper.fromString(OperatorState.class, Files.readString(watch.storage)).resourceVersion();
            } catch (final IOException | RuntimeException e) {
                logger.log(WARNING, "Can't load resource version", e);
            }
        }
        return null;
    }

    private void checkpoint(final Watch watch, final String version) {
        if (version == null || version.isBlank()) {
            return;
        }
        watch.resourceVersion = version; // resource versions are opaque, never compare or parse them
        if (watch.storage != null) {
            try {
                Files.createDirectories(watch.storage.getParent());
                // Inventory first: a crash can replay work, but must not advance the cursor without it.
                persist(watch.inventory, jsonMapper.toString(new EventList(
                        new ListMetadata(version), List.copyOf(watch.known.values()))));
                persist(watch.storage, jsonMapper.toString(new OperatorState(version)));
            } catch (final IOException e) {
                logger.log(WARNING, "Can't persist resource version", e);
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
        private final Path storage;
        private final Path inventory;
        private final Map<String, Map<String, Object>> known = new LinkedHashMap<>();
        private final ConcurrentLinkedDeque<Runnable> pending = new ConcurrentLinkedDeque<>();
        private final AtomicBoolean running = new AtomicBoolean();
        private volatile String resourceVersion;
        private volatile long delay = 100;
        private Flow.Subscription subscription;
        private CompletableFuture<?> request;
        private ScheduledFuture<?> retry;

        private Watch(final URI uri, final Path storage) {
            this.uri = uri;
            this.storage = storage;
            this.inventory = storage == null ? null : storage.resolveSibling(storage.getFileName() + ".items");
        }
    }

    private static class ClientHttpException extends RuntimeException {
        private final int status;

        private ClientHttpException(final int status) {
            super("Kubernetes HTTP status " + status);
            this.status = status;
        }
    }

    @JsonModel
    public record OperatorState(String resourceVersion) {
    }

    @JsonModel
    public record ListMetadata(String resourceVersion) {
    }

    @JsonModel
    public record EventList(ListMetadata metadata, List<Map<String, Object>> items) {
    }
}
