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
import io.yupiik.fusion.http.server.impl.DefaultEndpoint;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Endpoint {
    default int priority() {
        return 1000;
    }

    boolean matches(Request request);

    CompletionStage<Response> handle(Request request);

    /**
     * Factory enabling to instantiate an endpoint in a bean producer method easily.
     *
     * @param matcher  the predicate selecting the endpoint for usage.
     * @param handler  the endpoint implementation.
     * @param priority the endpoint priority.
     * @return the endpoint.
     */
    static Endpoint of(final Predicate<Request> matcher, final Function<Request, CompletionStage<Response>> handler,
                       final int priority) {
        return new DefaultEndpoint(priority, matcher, handler);
    }

    static Endpoint of(final Predicate<Request> matcher, final Function<Request, CompletionStage<Response>> handler) {
        return of(matcher, handler, 1000);
    }
}
