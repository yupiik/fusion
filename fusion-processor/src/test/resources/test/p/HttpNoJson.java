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
package test.p;

import io.yupiik.fusion.framework.build.api.http.HttpMatcher;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;

import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class HttpNoJson {
    @HttpMatcher(priority = 0)
    public CompletionStage<Response> all() {
        return completedFuture(Response.of().status(201).header("java-method", "all").build());
    }

    @HttpMatcher(methods = "GET")
    public Response get(final Request request) {
        return Response.of()
                .status(202)
                .header("java-method", "get")
                .header("req-m", request.method())
                .build();
    }

    @HttpMatcher(methods = "GET", pathMatching = HttpMatcher.PathMatching.EXACT, path = "/foo")
    public CompletionStage<Response> getAndFooPath() {
        return completedFuture(Response.of().status(203).header("java-method", "getAndFooPath").build());
    }

    @HttpMatcher(methods = "GET", pathMatching = HttpMatcher.PathMatching.ENDS_WITH, path = "/foo")
    public CompletionStage<Response> getAndEndsWithFooPath() {
        return completedFuture(Response.of().status(204).header("java-method", "getAndEndsWithFooPath").build());
    }

    @HttpMatcher(methods = "GET", pathMatching = HttpMatcher.PathMatching.STARTS_WITH, path = "/foo")
    public CompletionStage<Response> getAndStartsWithFooPath() {
        return completedFuture(Response.of().status(205).header("java-method", "getAndStartsWithFooPath").build());
    }

    @HttpMatcher(methods = "GET", pathMatching = HttpMatcher.PathMatching.REGEX, path = "/foo/.+/foo")
    public CompletionStage<Response> getAndRegexFooPath() {
        return completedFuture(Response.of().status(206).header("java-method", "getAndRegexFooPath").build());
    }
}
