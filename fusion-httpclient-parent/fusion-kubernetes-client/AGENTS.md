<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-kubernetes-client.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
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

