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
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
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
    private final List<Operator<?>> operatorSpecs;
    private final OperatorConfiguration configuration;
    private final KubernetesClient kubernetes;
    private final ScheduledExecutorService threads;
    private final JsonMapper jsonMapper;

    private final List<OperatorRuntime<?>> operators = new ArrayList<>();
    private HttpServer probes;

    public OperatorsLifecycle(final List<Operator<?>> operatorSpecs,
                              final OperatorConfiguration configuration,
                              final KubernetesClient kubernetes,
                              final ScheduledExecutorService threads,
                              final JsonMapper jsonMapper) {
        this.operatorSpecs = operatorSpecs;
        this.configuration = configuration;
        this.kubernetes = kubernetes;
        this.threads = threads;
        this.jsonMapper = jsonMapper;
    }

    @SuppressWarnings("unchecked")
    public void onStart(@OnEvent final Start ignored) { // force init
        startProbeIfNeeded();
        operators.addAll(operatorSpecs.stream()
                .map(spec -> new OperatorRuntime<>(configuration, kubernetes, threads, jsonMapper, spec))
                .toList());
        try {
            allOf(operators.stream()
                    .map(OperatorRuntime::doStart)
                    .toArray(CompletableFuture<?>[]::new))
                    .get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private void startProbeIfNeeded() {
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
                    switch (exchange.getRequestURI().getPath()) { // TODO
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

    @Destroy
    public void onStop() { // cleanup
        final var errors = new IllegalStateException("Some error occurred");
        operators.forEach(o -> {
            try {
                o.doStop();
            } catch (final RuntimeException re) {
                errors.addSuppressed(re);
            }
        });
        if (probes != null) {
            probes.stop(0);
        }
        if (errors.getSuppressed().length > 0) {
            throw errors;
        }
    }

    @Override
    public void await() {
        operators.forEach(OperatorRuntime::await);
    }
}
