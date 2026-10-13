---
name: fusion-http-server
description: Fusion `fusion-http-server` - HTTP server based on Apache Tomcat with a light request/response API and endpoint matchers (aggregates api, observability and tomcat modules). Use when working on or integrating this module, or
  when a task touches its annotations, entry points or configuration.
---

# fusion-http-server

HTTP server based on Apache Tomcat with a light request/response API and endpoint matchers (aggregates api, observability and tomcat modules).

# Fusion :: HTTP Server (`fusion-http-server`)

HTTP server based on Apache Tomcat with a light request/response API and endpoint matchers (aggregates api, observability and tomcat modules).

Backward-compatible aggregator of the HTTP server modules. It pulls `fusion-http-server-api`,
`fusion-http-server-observability` and `fusion-http-server-tomcat` together so existing consumers keeping
only `io.yupiik.fusion:fusion-http-server` on the classpath get the full Tomcat-based server.

For lighter setups, depend on the fine-grained modules instead:

- `fusion-http-server-api`: contracts and helpers only (no server engine).
- `fusion-http-server-tomcat`: the Tomcat implementation.
- `fusion-http-server-jre`: a Tomcat-free JDK `com.sun.net.httpserver` adapter (api only).
- `fusion-http-server-observability`: health/metrics API and endpoints.



## Working in this module

- Build it: `mvn install -pl fusion-http-server-parent/fusion-http-server -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.


## Skill relationship

This is the module-specific skill. For the cross-cutting Fusion mechanics (dependency triad, compile-time
annotation processing, writing `@Command` CLI beans, testing helpers, documentation) load the corresponding
concept skills: `fusion-project-setup`, `fusion-annotation-processing`, `fusion-cli`, `fusion-testing`,
`fusion-documentation`.
<!-- generated from fusion-build-internal agents/skills/module/SKILL.md - do not edit -->
