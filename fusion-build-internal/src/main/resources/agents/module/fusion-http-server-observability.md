# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

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

{{{footer}}}