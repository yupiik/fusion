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
package test.p;

import io.yupiik.fusion.framework.build.api.http.HttpJavaMatcher;
import io.yupiik.fusion.framework.build.api.http.HttpMatcher;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.json.JsonMapper;

import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class HttpJavaEndpoints {
    private final JsonMapper mapper;

    protected HttpJavaEndpoints() {
        this(null);
    }

    public HttpJavaEndpoints(final JsonMapper mapper) {
        this.mapper = mapper;
    }

    @HttpJavaMatcher(priority = 0)
    public CompletionStage<Response> all() {
        return completedFuture(Response.of().status(201).header("java-method", "all").build());
    }

    @HttpJavaMatcher("""
            "GET".equals(req.method())
            """)
    public Response get(final Request request) {
        return Response.of()
                .status(202)
                .header("java-method", "get")
                .header("req-m", request.method())
                .build();
    }

    @HttpJavaMatcher("""
            "GET".equals(req.method()) && "/foo".equals(req.path())
            """)
    public CompletionStage<Response> getAndFooPath() {
        return completedFuture(Response.of().status(203).header("java-method", "getAndFooPath").build());
    }

    @HttpJavaMatcher("""
            "GET".equals(req.method()) && req.path().endsWith("/foo")
            """)
    public CompletionStage<Response> getAndEndsWithFooPath() {
        return completedFuture(Response.of().status(204).header("java-method", "getAndEndsWithFooPath").build());
    }

    @HttpJavaMatcher("""
            "GET".equals(req.method()) && req.path().startsWith("/foo")
            """)
    public CompletionStage<Response> getAndStartsWithFooPath() {
        return completedFuture(Response.of().status(205).header("java-method", "getAndStartsWithFooPath").build());
    }

    @HttpJavaMatcher("""
            { // never use this kind of regex which recompiles per request the request, just for tests
                if (!"GET".equals(req.method())) { return false; }
                final var matcher = java.util.regex.Pattern.compile("/foo/.+/foo").matcher(req.path());
                if (matcher.matches()) {
                    req.setAttribute("fusion.http.matcher", matcher);
                    return true;
                }
                return false;
            }
            """)
    public CompletionStage<Response> getAndRegexFooPath() {
        return completedFuture(Response.of().status(206).header("java-method", "getAndRegexFooPath").build());
    }

    @HttpJavaMatcher("""
            "POST".equals(req.method()) && "/greetstage".equals(req.path())
            """)
    public CompletionStage<GreetResponse> greetStage(final GreetRequest request) {
        return completedFuture(new GreetResponse("Hello " + request.name() + "!"));
    }

    @HttpJavaMatcher("""
            "POST".equals(req.method()) && "/greet".equals(req.path())
            """)
    public GreetResponse greet(final GreetRequest request) {
        return new GreetResponse("Hello " + request.name() + "!");
    }

    @JsonModel public record GreetRequest(String name) {}
    @JsonModel public record GreetResponse(String message) {}
}
