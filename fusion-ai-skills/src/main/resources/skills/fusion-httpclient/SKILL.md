---
name: fusion-httpclient
description: Fusion `fusion-httpclient` - Extended java.net.http client: routing, throttling, retries, logging listeners. Use when working on or integrating this module, or
  when a task touches its annotations, entry points or configuration.
---

# fusion-httpclient

Extended java.net.http client: routing, throttling, retries, logging listeners.

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



## Skill relationship

This is the module-specific skill. For the cross-cutting Fusion mechanics (dependency triad, compile-time
annotation processing, writing `@Command` CLI beans, testing helpers, documentation) load the corresponding
concept skills: `fusion-project-setup`, `fusion-annotation-processing`, `fusion-cli`, `fusion-testing`,
`fusion-documentation`.
<!-- generated from fusion-build-internal agents/skills/module/SKILL.md - do not edit -->
