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
package io.yupiik.fusion.http.server.spi;

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;

import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedStage;

/**
 * When {@link io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration} has a {@link io.yupiik.fusion.http.server.impl.tomcat.MonitoringServerConfiguration}
 * and {@code fusion.http-server.monitoring.enabled} is {code true}, these endpoints are ignored from the main server and only added to the monitoring one.
 */
public interface MonitoringEndpoint extends BaseEndpoint {
    /**
     * @param body the error message or {@code null} (empty body).
     * @return a HTTP 503 response with the provided body.
     */
    default CompletionStage<Response> fail(final String body) {
        return completedStage(Response.of().status(503).body(body == null ? "" : body).build());
    }

    @Override
    default CompletionStage<Response> handle(final Request request) {
        try {
            return unsafeHandle(request);
        } catch (final Exception re) {
            return fail(re.getMessage());
        }
    }

    /**
     * Overriding this method instead of {@link #handle(Request)} will enable to get an implicit try/catch around it.
     * @param request the incoming request.
     * @return the response, an empty one with status 200 by default.
     */
    default CompletionStage<Response> unsafeHandle(Request request) throws Exception {
        return completedStage(Response.of().build());
    }
}
