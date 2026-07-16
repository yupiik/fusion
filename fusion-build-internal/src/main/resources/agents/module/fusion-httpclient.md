# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

Decorators around the JDK `java.net.http.HttpClient` - no custom HTTP stack.

## Entry points

- `io.yupiik.fusion.httpclient.core.ExtendedHttpClient` + `ExtendedHttpClientConfiguration`: main entry point
  (retries, logging, listeners).
- `io.yupiik.fusion.httpclient.core.RoutingHttpClient` / `ThrottledHttpClient` / `DelegatingHttpClient`: composable decorators.
- `listener` package: request/response listeners (used by `fusion-tracing`).

## Module rules

- Stay a thin layer over the JDK client: no third party HTTP dependency.

{{{footer}}}
