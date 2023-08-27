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
package io.yupiik.kubernetes.operator.base.impl;

import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.kubernetes.operator.base.spi.Operator;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.net.http.HttpResponse.BodyHandlers.fromLineSubscriber;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.util.Optional.ofNullable;
import static java.util.logging.Level.SEVERE;

public class OperatorRuntime<T extends ObjectLike> extends SimpleController<T> implements Awaiter {
    private final Logger logger = Logger.getLogger(getClass().getName());
    private final OperatorConfiguration configuration;
    private final KubernetesClient kubernetes;
    private final ExecutorService threads;
    private final JsonMapper jsonMapper;
    private final Operator<T> spec;

    private final List<URI> findAllUris;
    private final ReentrantLock lock = new ReentrantLock();
    private final CountDownLatch awaiter = new CountDownLatch(1);

    private String lastResource;

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
        this.spec = spec;

        // note: potentially handle multiple namespaces or configure them in the controller
        // /apis/$group/$version/namespaces/$namespace/$plural from crd.json (namespaces cause namespaced=true in crd.json)
        this.findAllUris = spec.namespaces().stream()
                .map(it -> URI.create("https://kubernetes.api/" +
                        "apis/" + spec.apiVersion() + "/" +
                        (spec.namespaced() ? "namespaces/" + it + "/" : "") +
                        spec.pluralName()))
                .toList();
    }

    public CompletionStage<?> doStart() {
        return spec.onStart().thenRunAsync(this::startListening, threads);
    }

    public void doStop() {
        try {
            super.stop();
            spec.onStop();
        } finally {
            if (awaiter.getCount() > 0) {
                awaiter.countDown();
            }
        }
    }

    private void startListening() {
        if (stopping) {
            return;
        }

        findAllUris.forEach(uri -> findAll(uri)
                .thenRunAsync(this::init, threads)
                .thenRunAsync(() -> watch(uri), threads)
                .exceptionally(e -> {
                    logger.log(SEVERE, e, () -> "Can't watch events: " + e.getMessage() + ", exiting");
                    awaiter.countDown();
                    throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
                }));
    }

    private void watch(final URI uri) {
        final var usedLastResource = lastResource;
        final var watchUri = URI.create(uri.toASCIIString() + "?watch=true" + (usedLastResource != null ? "&resourceVersion=" + usedLastResource : ""));
        logger.info(() -> "Starting to watch '" + watchUri + "'");
        get(watchUri, fromLineSubscriber(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                this.subscription = subscription;
                logger.info(() -> "Starting to watch resources");
                this.subscription.request(1);
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
            logger.log(SEVERE, e, () -> "Can't watch events: " + e.getMessage());
            if (!stopping) {// restart
                logger.info(() -> "Re-watching events after a failure");
                watch(uri);
            } else {
                logger.info(() -> "Application is shutting down, exiting");
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<List<T>> findAll(final URI findAllUri) {
        return get(findAllUri, ofString())
                .thenApply(res -> { // capture last resource version
                    final var simpleModel = (Map<String, Object>) jsonMapper.fromString(Object.class, res);
                    if ("Status".equals(simpleModel.getOrDefault("kind", "")) &&
                            200 != ofNullable(simpleModel.get("code"))
                                    .filter(Number.class::isInstance)
                                    .map(Number.class::cast)
                                    .map(Number::intValue)
                                    .orElse(200)) {
                        throw new IllegalStateException("Can't find all items: " + res);
                    }

                    final var meta = (Map<String, Object>) simpleModel.get("metadata");
                    if (meta == null) {
                        return null;
                    }
                    lock.lock();
                    try {
                        lastResource = ofNullable(meta.get("resourceVersion")).map(String::valueOf).orElse(null);
                    } finally {
                        lock.unlock();
                    }
                    return ofNullable(simpleModel.get("items"))
                            .filter(Collection.class::isInstance)
                            .map(it -> ((Collection<Object>) it).stream()
                                    .map(i -> jsonMapper.fromString(spec.resourceType(), jsonMapper.toString(i)))
                                    .toList())
                            .orElse(List.of());
                });
    }

    private <O> CompletionStage<O> get(final URI findAllUri, final HttpResponse.BodyHandler<O> handler) {
        return kubernetes.sendAsync(
                        HttpRequest.newBuilder()
                                .GET()
                                .uri(findAllUri)
                                .header("accept", "application/json")
                                .build(),
                        handler)
                .thenApplyAsync(res -> {
                    if (res.statusCode() != 200) {
                        throw new IllegalStateException("Invalid response: " + res + "\n" + res.body());
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
}
