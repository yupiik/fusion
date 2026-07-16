# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

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

{{{footer}}}
