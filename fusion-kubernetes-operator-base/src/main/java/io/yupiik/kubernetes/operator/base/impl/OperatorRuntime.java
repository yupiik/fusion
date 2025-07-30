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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.net.http.HttpResponse.BodyHandlers.fromLineSubscriber;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.util.Optional.ofNullable;
import static java.util.logging.Level.SEVERE;
import static java.util.stream.Collectors.joining;

public class OperatorRuntime<T extends ObjectLike> extends SimpleController<T> implements Awaiter {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final OperatorConfiguration configuration;
    private final KubernetesClient kubernetes;
    private final ExecutorService threads;
    private final JsonMapper jsonMapper;
    private final Path storage;

    private final List<URI> findAllUris;
    private final ReentrantLock lock = new ReentrantLock();
    private final CountDownLatch awaiter = new CountDownLatch(1);

    private Flow.Subscription subscription;
    private long lastResource;

    public OperatorRuntime(final OperatorConfiguration configuration,
                           final KubernetesClient kubernetes,
                           final ScheduledExecutorService threads,
                           final JsonMapper jsonMapper,
                           final Operator<T> spec) {
        super(threads, jsonMapper, configuration.eventThreadCount(), new OperatorWrapper<>(spec, configuration));

        final var wrapper = (OperatorWrapper<T>) operator;
        wrapper.onBookmark = this::onBookmark;
        wrapper.lastResource = () -> lastResource;

        this.configuration = configuration;
        this.kubernetes = kubernetes;
        this.threads = threads;
        this.jsonMapper = jsonMapper;

        this.storage = configuration.storage() == null ? null : Path.of(configuration.storage()).resolve(spec.pluralName());
        if (storage != null && !Files.exists(storage.getParent())) {
            logger.info(() -> "Creating '" + storage.getParent() + "'");
            try {
                Files.createDirectories(storage.getParent());
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        // note: potentially handle multiple namespaces or configure them in the controller
        // /apis/$group/$version/namespaces/$namespace/$plural from crd.json (namespaces cause namespaced=true in crd.json)
        this.findAllUris = spec.namespaces().stream()
                .map(it -> URI.create("https://kubernetes.api/" +
                        "apis/" + spec.apiVersion() + "/" +
                        (spec.namespaced() ? "namespaces/" + it + "/" : "") +
                        spec.pluralName()))
                .toList();
        logger.info(() -> "Will watch " + findAllUris.stream().map(URI::getPath).collect(joining(", ", "[", "]")));
    }

    @Override
    public void stop() {
        stopWatching();

        // ensure http client request (watch) is done
        await();
    }

    public CompletionStage<?> doStart() {
        return operator.onStart().thenRunAsync(this::startListening, threads);
    }

    public void doStop() {
        try {
            stopWatching();
            operator.onStop();
        } finally {
            if (awaiter.getCount() > 0) {
                awaiter.countDown();
            }
        }
    }

    private void stopWatching() {
        super.stop();

        final var subscription = this.subscription;
        if (subscription != null) {
            subscription.cancel();
        }
    }

    private void startListening() {
        if (stopping) {
            return;
        }

        findAllUris.forEach(uri -> validateResourceVersion(uri)
                .thenRunAsync(this::init, threads)
                .thenRunAsync(() -> watch(uri), threads)
                .exceptionally(e -> {
                    logger.log(SEVERE, e, () -> "Can't watch events: " + e.getMessage() + ", exiting");
                    awaiter.countDown();
                    throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
                }));
    }

    @Override
    protected void onBookmark(final String resourceVersion) {
        final var current = lastResource;
        if (current > 0 && isHigher(current, resourceVersion)) {
            return;
        }
        lock.lock();
        try {
            lastResource = Long.parseLong(resourceVersion);
            if (storage != null) {
                try {
                    Files.writeString(storage, jsonMapper.toString(new OperatorState(resourceVersion)));
                } catch (final IOException | RuntimeException e) {
                    logger.log(SEVERE, e, () -> "Can't write '" + storage + "': " + e.getMessage());
                }
            }
        } catch (final NumberFormatException nfe) {
            logger.warning(() -> "Can't parse '" + resourceVersion + "'");
        } finally {
            lock.unlock();
        }
    }

    private boolean isHigher(final long current, final String resourceVersion) {
        try {
            return current > Long.parseLong(resourceVersion);
        } catch (final NumberFormatException nfe) {
            return false;
        }
    }

    private void watch(final URI uri) {
        final var usedLastResource = lastResource;
        final var originalUri = uri.toASCIIString();
        final var watchUri = URI.create(originalUri + (originalUri.contains("?") ? "&" : "?") +
                "watch=true" +
                (configuration.useBookmarks() ? "&allowWatchBookmarks=true" : "") +
                (usedLastResource > 0 ? "&resourceVersion=" + usedLastResource : ""));
        logger.info(() -> "Starting to watch '" + watchUri + "'");
        httpGet(watchUri, fromLineSubscriber(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                OperatorRuntime.this.subscription = subscription;
                logger.info(() -> "Starting to watch resources");
                OperatorRuntime.this.subscription.request(1);
            }

            @Override
            public void onNext(final String item) {
                try {
                    OperatorRuntime.super.onEvent(item);
                } finally {
                    subscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable throwable) {
                logger.log(SEVERE, throwable, throwable::getMessage);
            }

            @Override
            public void onComplete() {
                // no-op
            }
        })).exceptionally(e -> {
            if (!stopping) {// restart
                logger.log(SEVERE, e, () -> "Can't watch events: " + e.getMessage() + ", relaunching the watch");
                watch(uri);
            } else {
                logger.info(() -> "Application is shutting down, exiting");
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Long> validateResourceVersion(final URI base) {
        final long resourceVersion = loadResourceVersion();
        final var query = "limit=1"/* we just check the resource version */ + (resourceVersion > 0 ? "&resourceVersion=" + resourceVersion : "");
        final var uri = URI.create(base.toASCIIString() + (base.getRawQuery() == null || base.getRawQuery().isBlank() ? "?" : "&") + query);
        return httpGet(uri, ofString())
                .exceptionallyCompose(err -> {
                    logger.log(SEVERE, err, () -> "Can't load initial state: " + err.getMessage());

                    var unwrapped = err instanceof CompletionException ce ? ce.getCause() : err;
                    // there was a resource version and it is outdated, let's try to skip it to restart "clean"
                    if (unwrapped instanceof ClientHttpException che && che.status == 410 /* gone */) {
                        logger.info(() -> "Deleting state since load failed");
                        try {
                            Files.delete(storage);
                        } catch (final IOException e) {
                            logger.log(SEVERE, e, () -> "Can't delete state: " + e.getMessage());
                        }
                        return httpGet(base, ofString());
                    }

                    throw unwrapped instanceof RuntimeException re ? re : new IllegalStateException(unwrapped);
                })
                .thenApply(res -> { // capture last resource version
                    final var simpleModel = (Map<String, Object>) jsonMapper.fromString(Object.class, res);
                    if ("Status".equals(simpleModel.getOrDefault("kind", "")) &&
                            200 != ofNullable(simpleModel.get("code"))
                                    .filter(Number.class::isInstance)
                                    .map(Number.class::cast)
                                    .map(Number::intValue)
                                    .orElse(200)) {
                        throw new IllegalStateException("Can't GET '" + base + "': " + res);
                    }

                    final var meta = (Map<String, Object>) simpleModel.get("metadata");
                    if (meta == null) { // unlikely
                        return -1L;
                    }

                    final var newResourceVersion = ofNullable(meta.get("resourceVersion")).map(String::valueOf).orElse(null);
                    if (newResourceVersion != null) {
                        long newResourceVersionLong = -1;
                        try {
                            newResourceVersionLong = Long.parseLong(newResourceVersion);
                        } catch (final NumberFormatException ignored) {
                            logger.finest(() -> "Can't parse new resource version: '" + newResourceVersion + "'");
                        }

                        if (newResourceVersionLong < resourceVersion) { // something went wrong, go back in time, todo: should we delete it?
                            logger.warning(() -> "Current actual resource version is before persisted resource version");
                            onBookmark(newResourceVersion);
                            return newResourceVersionLong;
                        }
                        return resourceVersion;
                    }

                    return -1L;
                });
    }

    private long loadResourceVersion() {
        if (storage == null || !Files.exists(storage)) {
            return -1;
        }
        try {
            final var state = jsonMapper.fromString(OperatorState.class, Files.readString(storage));
            if (state.resourceVersion() != null) {
                logger.info(() -> "Using resourceVersion=" + state.resourceVersion());
                try {
                    final long value = Long.parseLong(state.resourceVersion());
                    lastResource = value;
                    return value;
                } catch (final NumberFormatException ignored) {
                    logger.finest(() -> "Can't parse resource version: '" + state.resourceVersion() + "'");
                }
            }
        } catch (final IOException e) {
            logger.log(SEVERE, e, () -> "Can't load state: " + e.getMessage());
        }
        return -1;
    }

    private <O> CompletionStage<O> httpGet(final URI uri, final HttpResponse.BodyHandler<O> handler) {
        return kubernetes.sendAsync(
                        HttpRequest.newBuilder()
                                .GET()
                                .uri(uri)
                                .header("accept", "application/json")
                                .build(),
                        handler)
                .thenApplyAsync(res -> {
                    if (res.statusCode() != 200) {
                        throw new ClientHttpException(() -> "Invalid response: " + res + "\n" + res.body(), res.statusCode());
                    }
                    return res.body();
                }, threads);
    }

    @Override
    public void await() {
        if (!configuration.await()) {
            return;
        }

        // await process termination (kill sig)
        try {
            awaiter.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class ClientHttpException extends RuntimeException {
        private final int status;
        private final Supplier<String> message;

        private ClientHttpException(final Supplier<String> message, final int status) {
            super("");
            this.message = message;
            this.status = status;
        }

        @Override
        public String getMessage() {
            return message.get();
        }
    }

    @JsonModel
    public record OperatorState(String resourceVersion) {
    }

    private static class OperatorWrapper<T extends ObjectLike> implements Operator<T> {
        private final Operator<T> spec;
        private final OperatorConfiguration configuration;
        private Consumer<String> onBookmark;
        private LongSupplier lastResource;

        private OperatorWrapper(final Operator<T> spec, OperatorConfiguration configuration) {
            this.spec = spec;
            this.configuration = configuration;
        }

        private void onEvent(final T resource, final OperatorConfiguration configuration, final Consumer<T> handler) {
            if (notAlreadySeen(resource)) {
                handler.accept(resource);
            }
            if (!configuration.useBookmarks()) {
                onSeen(resource);
            }
        }

        private void onSeen(final T resource) {
            if (resource.metadata().resourceVersion() == null) {
                return;
            }
            try {
                onBookmark.accept(resource.metadata().resourceVersion());
            } catch (final NumberFormatException nfe) {
                // no-op
            }
        }

        private boolean notAlreadySeen(final T resource) {
            if (resource.metadata().resourceVersion() == null) {
                return true;
            }
            try {
                return lastResource.getAsLong() < Long.parseLong(resource.metadata().resourceVersion());
            } catch (final NumberFormatException nfe) {
                return false;
            }
        }

        @Override
        public void onAdd(final T resource) {
            onEvent(resource, configuration, spec::onAdd);
        }

        @Override
        public void onModify(final T resource) {
            onEvent(resource, configuration, spec::onModify);
        }

        @Override
        public void onDelete(final T resource) {
            onEvent(resource, configuration, spec::onDelete);
        }

        @Override
        public void onError(final String error) {
            spec.onError(error);
        }

        @Override
        public void onBookmark(final String resourceVersion) {
            spec.onBookmark(resourceVersion);
        }

        @Override
        public CompletionStage<?> onStart() {
            return spec.onStart();
        }

        @Override
        public void onStop() {
            spec.onStop();
        }

        @Override
        public Class<T> resourceType() {
            return spec.resourceType();
        }

        @Override
        public String pluralName() {
            return spec.pluralName();
        }

        @Override
        public String apiVersion() {
            return spec.apiVersion();
        }

        @Override
        public boolean namespaced() {
            return spec.namespaced();
        }

        @Override
        public List<String> namespaces() {
            return spec.namespaces();
        }
    }
}
