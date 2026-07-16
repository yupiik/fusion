<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-observability.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: Observability (`fusion-observability`)

Observability HTTP server exposing health checks and OpenMetrics metrics.

Dedicated monitoring HTTP server (separate port from the business server) exposing health checks and
OpenMetrics/Prometheus-format metrics.

## Entry points

- `io.yupiik.fusion.observability.http.ObservabilityServer`: the monitoring server.
- `io.yupiik.fusion.observability.health.HealthCheck` / `HealthRegistry`: health SPI and registry.
- `io.yupiik.fusion.observability.metrics.MetricsRegistry` / `OpenMetricsFormatter`: metrics SPI and rendering.
- Endpoints implement `MonitoringEndpoint` from `fusion-http-server`.

## Module rules

- Health endpoints are used by orchestrators (Kubernetes probes): keep them non-blocking and cheap.



## Working in this module

- Build it: `mvn install -pl fusion-observability -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

