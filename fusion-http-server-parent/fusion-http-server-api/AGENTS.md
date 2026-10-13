<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-http-server-api.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: HTTP Server API (`fusion-http-server-api`)

HTTP server contracts (Request/Response/WebServer/Endpoint) and server-agnostic helpers, without any server engine.

Server-agnostic HTTP contracts and helpers. This module carries NO server engine and NO third party HTTP
dependency: `WebServer`, `Request`/`Response`/`Cookie`/`Body`, the `Endpoint`/`BaseEndpoint` SPI and the
shared publishers/aggregators/dates/matchers.

Endpoints are declared with `@HttpMatcher` (compiled to `Endpoint` beans by the processor) or implemented
as `Endpoint` beans directly:

```java
// preferred: declarative matcher on a bean method (Request parameter and CompletionStage are optional)
@HttpMatcher(method = "GET", pathMatching = EXACT, path = "/greet")
public CompletionStage<Response> greet(final Request request) {
    return completedFuture(Response.of()
            .status(200)
            .header("content-type", "application/json")
            .body("{\"hello\":true}")
            .build());
}

// alternative: implement Endpoint directly as a bean
@Bean
public class Greeting implements Endpoint {
    @Override
    public boolean matches(final Request request) {
        return "GET".equals(request.method());
    }

    @Override
    public CompletionStage<Response> handle(final Request request) {
        return completedFuture(Response.of().body("{\"hello\":true}").build());
    }
}
```

## Entry points

- `io.yupiik.fusion.http.server.api.WebServer` / `WebServerProvider`: server bootstrap/configuration and the
  single ServiceLoader-discovered provider behind `WebServer.of()` / `WebServer.Configuration.of()` and
  `Cookie.of()` (`create(Configuration)` + `createCookie()`; statically cached, overridable with
  `WebServerProvider.setProvider(...)`).
- `io.yupiik.fusion.http.server.api.Request` / `Response` / `Cookie` / `Body`: HTTP abstraction.
- `io.yupiik.fusion.http.server.spi.Endpoint` / `BaseEndpoint`: endpoint SPI the generated matchers implement.
- `io.yupiik.fusion.http.server.spi.MonitoringEndpoint`: endpoint SPI for health/metrics (implemented in the
  observability module).
- `io.yupiik.fusion.http.server.impl.*`: shared server-agnostic helpers (publishers, aggregators, dates, matchers).

## Module rules

- Keep it dependency-light and server-agnostic: no engine (Tomcat, ...), no `jakarta.servlet`, no third party
  runtime dependency beyond `fusion-api`.
- Response bodies use `java.util.concurrent.Flow` publishers: never block in helpers.
- The static factories fail with a clear `IllegalStateException` when no `WebServerProvider` is on the
  classpath (e.g. when only this module is used without a server implementation).



## Working in this module

- Build it: `mvn install -pl fusion-http-server-parent/fusion-http-server-api -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.
