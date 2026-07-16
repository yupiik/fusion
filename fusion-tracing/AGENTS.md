<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-tracing.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: Tracing (`fusion-tracing`)

Zipkin oriented tracing for the Fusion HTTP server and clients.

Zipkin-format span collection and flushing for the Fusion HTTP server (Tomcat valve) and the extended HTTP
client (listener), with OpenTelemetry-compatible export.

## Entry points

- `io.yupiik.fusion.tracing.server.TracingValve` + `ServerTracingConfiguration`: server side span creation.
- `io.yupiik.fusion.tracing.client.TracingListener` + `ClientTracingConfiguration`: client side propagation.
- `io.yupiik.fusion.tracing.collector.AccumulatingSpanCollector`: buffering collector.
- `io.yupiik.fusion.tracing.zipkin.ZipkinFlusher`: HTTP flusher of the accumulated spans.
- `io.yupiik.fusion.tracing.span.Span`: span model.

## Module rules

- Span flushing is asynchronous and batched: mind thread-safety, the collector is shared.



## Working in this module

- Build it: `mvn install -pl fusion-tracing -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

