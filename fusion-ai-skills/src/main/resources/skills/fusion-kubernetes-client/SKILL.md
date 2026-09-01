---
name: fusion-kubernetes-client
description: Fusion `fusion-kubernetes-client` - Lightweight Kubernetes client based on the JDK HTTP client (in-cluster and kubeconfig support). Use when working on or integrating this module, or
  when a task touches its annotations, entry points or configuration.
---

# fusion-kubernetes-client

Lightweight Kubernetes client based on the JDK HTTP client (in-cluster and kubeconfig support).

# Fusion :: HTTP Client Parent :: Kubernetes Client (`fusion-kubernetes-client`)

Lightweight Kubernetes client based on the JDK HTTP client (in-cluster and kubeconfig support).

An `HttpClient` facade preconfigured for the Kubernetes API server: in-cluster token/CA handling, kubeconfig
support, and websocket helpers. JSON payloads are handled by the caller (typically with `fusion-json`).

## Entry points

- `io.yupiik.fusion.kubernetes.client.KubernetesClient`: the client (implements `HttpClient`).
- `io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration`: token/certificates/API base configuration.
- `io.yupiik.fusion.kubernetes.client.WebSocketBuilderDelegate`: websocket support (exec/watch style usage).

## Module rules

- Keep it reflectionless and dependency-light: it must stay usable in GraalVM native operators.
- Token and certificates are refreshed from files: preserve that behavior for in-cluster long-running processes.



## Working in this module

- Build it: `mvn install -pl fusion-httpclient-parent/fusion-kubernetes-client -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.



## Skill relationship

This is the module-specific skill. For the cross-cutting Fusion mechanics (dependency triad, compile-time
annotation processing, writing `@Command` CLI beans, testing helpers, documentation) load the corresponding
concept skills: `fusion-project-setup`, `fusion-annotation-processing`, `fusion-cli`, `fusion-testing`,
`fusion-documentation`.
<!-- generated from fusion-build-internal agents/skills/module/SKILL.md - do not edit -->
