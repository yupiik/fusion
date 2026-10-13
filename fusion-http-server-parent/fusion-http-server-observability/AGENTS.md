<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-http-server-observability.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: HTTP Server Observability (`fusion-http-server-observability`)

Health check and metrics APIs plus the default /health and /metrics endpoint implementations for the Fusion HTTP server.

Health check and metrics APIs and their default `/health` and `/metrics` endpoint implementations.

## Entry points

- `io.yupiik.fusion.http.server.observability.HealthCheck` / `HealthRegistry`: register and run health checks.
- `io.yupiik.fusion.http.server.observability.MetricsRegistry` / `OpenMetricsFormatter`: metrics registry and
  OpenMetrics rendering.
- `io.yupiik.fusion.http.server.spi.MonitoringEndpoint` (from `fusion-http-server-api`): the SPI implemented by
  the default endpoints.
- `io.yupiik.fusion.http.server.impl.observability.Health` / `Metrics`: the `/health` and `/metrics` endpoint
  implementations exposed as `MonitoringEndpoint` beans by the Tomcat module's `ObservabilityBeans`.

## Module rules

- Pure library + endpoint implementations: this module depends only on `fusion-http-server-api`; it does NOT
  register the beans (that happens in `fusion-http-server-tomcat`).
- Keep it dependency-light and server-agnostic.



## Working in this module

- Build it: `mvn install -pl fusion-http-server-parent/fusion-http-server-observability -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.
