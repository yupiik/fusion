# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

Dedicated monitoring HTTP server (separate port from the business server) exposing health checks and
OpenMetrics/Prometheus-format metrics.

## Entry points

- `io.yupiik.fusion.observability.http.ObservabilityServer`: the monitoring server.
- `io.yupiik.fusion.observability.health.HealthCheck` / `HealthRegistry`: health SPI and registry.
- `io.yupiik.fusion.observability.metrics.MetricsRegistry` / `OpenMetricsFormatter`: metrics SPI and rendering.
- Endpoints implement `MonitoringEndpoint` from `fusion-http-server`.

## Module rules

- Health endpoints are used by orchestrators (Kubernetes probes): keep them non-blocking and cheap.

{{{footer}}}
