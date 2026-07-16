# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

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
- `io.yupiik.fusion.http.server.spi.MonitoringEndpoint`: endpoints for the observability server.
- `io.yupiik.fusion.http.server.impl.tomcat`: Tomcat wiring (internal).

## Module rules

- Tomcat (`tomcat-*` artifacts) is the ONLY third party runtime dependency family here; keep it that way.
- Response bodies use `java.util.concurrent.Flow` publishers: never block Tomcat threads in helpers.
- Tests run in parallel: always bind test servers to port `0` (random port), never a fixed port.

{{{footer}}}
