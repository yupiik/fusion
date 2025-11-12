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
package io.yupiik.fusion.http.server.observability.metrics;

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.spi.Endpoint;

import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class Metrics implements Endpoint {
    private final MetricsRegistry registry;
    private final OpenMetricsFormatter formatter = new OpenMetricsFormatter();
    private final String metricsPath;

    public Metrics(final MetricsRegistry registry, final String metricsPath) {
        this.registry = registry;
        this.metricsPath = metricsPath;
    }

    @Override
    public boolean matches(final Request request) {
        return "GET".equalsIgnoreCase(request.method()) && metricsPath.equalsIgnoreCase(request.path());
    }

    @Override
    public CompletionStage<Response> handle(final Request request) {
        request.setAttribute("skip-access-log", true);
        return completedFuture(Response.of()
                .status(200)
                .header("content-type", "text/plain")
                .body(formatter.apply(registry.entries()))
                .build());
    }
}