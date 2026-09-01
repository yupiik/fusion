---
name: fusion-tracing
description: Fusion `fusion-tracing` - Zipkin oriented tracing for the Fusion HTTP server and clients. Use when working on or integrating this module, or
  when a task touches its annotations, entry points or configuration.
---

# fusion-tracing

Zipkin oriented tracing for the Fusion HTTP server and clients.

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



## Skill relationship

This is the module-specific skill. For the cross-cutting Fusion mechanics (dependency triad, compile-time
annotation processing, writing `@Command` CLI beans, testing helpers, documentation) load the corresponding
concept skills: `fusion-project-setup`, `fusion-annotation-processing`, `fusion-cli`, `fusion-testing`,
`fusion-documentation`.
<!-- generated from fusion-build-internal agents/skills/module/SKILL.md - do not edit -->
