---
name: fusion-http-server
description: Fusion `fusion-http-server` - HTTP server based on Apache Tomcat with a light request/response API and endpoint matchers. Use when working on or integrating this module, or
  when a task touches its annotations, entry points or configuration.
---

# fusion-http-server

HTTP server based on Apache Tomcat with a light request/response API and endpoint matchers.

# Fusion :: Http Server (`fusion-http-server`)

HTTP server based on Apache Tomcat with a light request/response API and endpoint matchers.

Embeds Apache Tomcat behind a small reactive-friendly `Request`/`Response` API; endpoints are declared with
`@HttpMatcher` (compiled to `Endpoint` beans by the processor) or implemented as `Endpoint` beans directly.

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

- `io.yupiik.fusion.http.server.api.WebServer`: server bootstrap/configuration (`WebServer.Configuration` bean).
- `io.yupiik.fusion.http.server.api.Request` / `Response` / `Cookie` / `Body`: HTTP abstraction.
- `io.yupiik.fusion.http.server.spi.Endpoint` / `BaseEndpoint`: endpoint SPI the generated matchers implement.
- `io.yupiik.fusion.http.server.spi.MonitoringEndpoint`: endpoints served by the monitoring server (a separate
  Tomcat connector/port) for health checks and metrics.
- `io.yupiik.fusion.http.server.observability`: public health/metrics API (`HealthCheck`/`HealthRegistry`,
  `MetricsRegistry`/`OpenMetricsFormatter`); default `/health` and `/metrics` endpoints live in
  `io.yupiik.fusion.http.server.impl.observability` and are wired manually in
  `io.yupiik.fusion.http.server.impl.bean.ObservabilityBeans` (this module cannot use the processor due to the
  dependency cycle).
- `io.yupiik.fusion.http.server.impl.tomcat`: Tomcat wiring (internal).

## Module rules

- Tomcat (`tomcat-*` artifacts) is the ONLY third party runtime dependency family here; keep it that way.
- Response bodies use `java.util.concurrent.Flow` publishers: never block Tomcat threads in helpers.
- Tests run in parallel: always bind test servers to port `0` (random port), never a fixed port.



## Working in this module

- Build it: `mvn install -pl fusion-http-server -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.



## Skill relationship

This is the module-specific skill. For the cross-cutting Fusion mechanics (dependency triad, compile-time
annotation processing, writing `@Command` CLI beans, testing helpers, documentation) load the corresponding
concept skills: `fusion-project-setup`, `fusion-annotation-processing`, `fusion-cli`, `fusion-testing`,
`fusion-documentation`.
<!-- generated from fusion-build-internal agents/skills/module/SKILL.md - do not edit -->
