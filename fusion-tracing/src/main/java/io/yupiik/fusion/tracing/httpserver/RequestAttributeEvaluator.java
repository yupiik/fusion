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
package io.yupiik.fusion.tracing.httpserver;

import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.tracing.request.PendingSpan;

import java.util.function.Supplier;

public class RequestAttributeEvaluator implements Supplier<PendingSpan> {
    private final Request request;

    public RequestAttributeEvaluator(final Request request) {
        this.request = request;
    }

    @Override
    public PendingSpan get() {
        return request.attribute(PendingSpan.class.getName(), PendingSpan.class);
    }
}
