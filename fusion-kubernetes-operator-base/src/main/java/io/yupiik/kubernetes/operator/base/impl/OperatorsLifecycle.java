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

import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.kubernetes.client.KubernetesClient;
import io.yupiik.kubernetes.operator.base.spi.Operator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.CompletableFuture.allOf;

@ApplicationScoped
public class OperatorsLifecycle implements Awaiter {
    private final RuntimeContainer container;
    private final List<Instance<?>> handles = new ArrayList<>();
    private final List<OperatorRuntime<?>> operators = new ArrayList<>();
    private HttpServer probes;

    public OperatorsLifecycle(final RuntimeContainer container) {
        this.container = container;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onStart(@OnEvent final Start ignored) {
        // Do not even parse runtime configuration or instantiate producers in metadata containers.
        try (final var configuration = container.lookup(Configuration.class)) {
            if (!configuration.instance().get("operator.enabled").map(Boolean::parseBoolean).orElse(false)) {
                return;
            }
        }
        try {
            final var configuration = lookup(OperatorConfiguration.class);
            final var kubernetes = lookup(KubernetesClient.class);
            final var threads = lookup(ScheduledExecutorService.class);
            final var jsonMapper = lookup(JsonMapper.class);
            final var specs = container.lookups(Operator.class);
            handles.add(specs);
            specs.instance().forEach(spec -> operators.add(new OperatorRuntime<>(configuration, kubernetes, threads, jsonMapper, spec)));
            startProbeIfNeeded(configuration, threads);
            allOf(operators.stream()
                    .map(OperatorRuntime::doStart)
                    .toArray(CompletableFuture<?>[]::new))
                    .get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw cleanup(new IllegalStateException(e));
        } catch (final ExecutionException e) {
            throw cleanup(new IllegalStateException(e.getCause()));
        } catch (final RuntimeException e) {
            throw cleanup(e);
        }
    }

    private <T> T lookup(final Class<T> type) {
        final var handle = container.lookup(type);
        handles.add(handle);
        return handle.instance();
    }

    private RuntimeException cleanup(final RuntimeException failure) {
        try {
            onStop(new Stop());
        } catch (final RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        return failure;
    }

    private void startProbeIfNeeded(final OperatorConfiguration configuration, final ScheduledExecutorService threads) {
        if (configuration.probePort() < 0) {
            return;
        }
        try {
            probes = HttpServer.create(new InetSocketAddress(configuration.probePort()), 128);
            probes.setExecutor(threads);
            probes.createContext("/", exchange -> {
                try (exchange) {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(404, 0);
                        return;
                    }
                    switch (exchange.getRequestURI().getPath()) {
                        case "/health" -> exchange.sendResponseHeaders(200, 0);
                        case "/metrics" -> exchange.sendResponseHeaders(200, 0);
                        default -> exchange.sendResponseHeaders(404, 0);
                    }
                }
            });
            probes.start();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // Stop watches before the container closes application-owned HTTP clients and executors.
    public void onStop(@OnEvent final Stop stop) {
        final var errors = new IllegalStateException("Some error occurred");
        operators.forEach(o -> {
            try {
                o.doStop();
            } catch (final RuntimeException re) {
                errors.addSuppressed(re);
            }
        });
        operators.clear();
        if (probes != null) {
            probes.stop(0);
            probes = null;
        }
        // Release lookup handles only after runtimes stop. Application resources remain container-owned.
        for (int i = handles.size() - 1; i >= 0; i--) {
            try {
                handles.get(i).close();
            } catch (final RuntimeException re) {
                errors.addSuppressed(re);
            }
        }
        handles.clear();
        if (errors.getSuppressed().length > 0) {
            throw errors;
        }
    }

    @Override
    public void await() {
        List.copyOf(operators).forEach(OperatorRuntime::await);
    }
}
