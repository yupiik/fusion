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
package io.yupiik.fusion.observability.health;

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.spi.Endpoint;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

public class Health implements Endpoint {
    private final HealthRegistry healthChecks;

    public Health(final HealthRegistry healthChecks) {
        this.healthChecks = healthChecks;
    }

    @Override
    public boolean matches(final Request request) {
        return "GET".equalsIgnoreCase(request.method()) && "/health".equalsIgnoreCase(request.path());
    }

    @Override
    public CompletionStage<Response> handle(final Request request) {
        final var query = request.query();
        final var filter = query != null && query.startsWith("type=") ?
                query.substring("type=".length()) : null;

        var healthCheckStream = healthChecks.healthChecks().stream();
        if (filter != null) {
            healthCheckStream = healthCheckStream.filter(c -> Objects.equals(c.type(), filter));
        }
        final var checks = healthCheckStream
                .collect(toMap(identity(), c -> c.check().toCompletableFuture()));
        return allOf(checks.values().toArray(new CompletableFuture<?>[0]))
                .thenApply(success -> success(checks))
                .exceptionally(failed -> failure(checks));
    }

    private Response failure(final Map<HealthCheck, CompletableFuture<HealthCheck.Result>> checks) {
        final var response = Response.of()
                .status(503)
                .header("content-type", "text/plain")
                .body(checks.entrySet().stream()
                        .map(c -> {
                            try {
                                return toSuccessLine(c);
                            } catch (final CompletionException | CancellationException ce) {
                                return c.getKey().name() + ",KO,\"" + ofNullable(ce.getMessage()).map(this::escape).orElse("") + "\"";
                            }
                        })
                        .collect(joining("\n")))
                .build();

        // cancel if any is still pending
        checks.values().stream()
                .filter(it -> !it.isCompletedExceptionally())
                .forEach(it -> {
                    try {
                        it.cancel(true);
                    } catch (final RuntimeException re) {
                        // no-op
                    }
                });
        return response;
    }

    private Response success(final Map<HealthCheck, CompletableFuture<HealthCheck.Result>> checks) {
        final var hasFailure = checks.values().stream()
                .anyMatch(it -> it.getNow(null).status() == HealthCheck.Status.KO);
        return Response.of()
                .status(hasFailure ? 503 : 200)
                .header("content-type", "text/plain")
                .body(checks.entrySet().stream()
                        .map(this::toSuccessLine)
                        .collect(joining("\n")))
                .build();
    }

    private String toSuccessLine(final Map.Entry<HealthCheck, CompletableFuture<HealthCheck.Result>> c) {
        final var result = c.getValue().getNow(null);
        return c.getKey().name() + "," + result.status() + (result.message() != null ? ",\"" + escape(result.message()) + '"' : "");
    }

    private String escape(final String string) {
        return string.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
