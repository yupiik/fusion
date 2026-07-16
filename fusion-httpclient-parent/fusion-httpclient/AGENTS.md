<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-httpclient.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: HTTP Client (`fusion-httpclient`)

Extended java.net.http client: routing, throttling, retries, logging listeners.

Decorators around the JDK `java.net.http.HttpClient` - no custom HTTP stack.

## Entry points

- `io.yupiik.fusion.httpclient.core.ExtendedHttpClient` + `ExtendedHttpClientConfiguration`: main entry point
  (retries, logging, listeners).
- `io.yupiik.fusion.httpclient.core.RoutingHttpClient` / `ThrottledHttpClient` / `DelegatingHttpClient`: composable decorators.
- `listener` package: request/response listeners (used by `fusion-tracing`).

## Module rules

- Stay a thin layer over the JDK client: no third party HTTP dependency.



## Working in this module

- Build it: `mvn install -pl fusion-httpclient-parent/fusion-httpclient -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

