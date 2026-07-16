# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

An `HttpClient` facade preconfigured for the Kubernetes API server: in-cluster token/CA handling, kubeconfig
support, and websocket helpers. JSON payloads are handled by the caller (typically with `fusion-json`).

## Entry points

- `io.yupiik.fusion.kubernetes.client.KubernetesClient`: the client (implements `HttpClient`).
- `io.yupiik.fusion.kubernetes.client.KubernetesClientConfiguration`: token/certificates/API base configuration.
- `io.yupiik.fusion.kubernetes.client.WebSocketBuilderDelegate`: websocket support (exec/watch style usage).

## Module rules

- Keep it reflectionless and dependency-light: it must stay usable in GraalVM native operators.
- Token and certificates are refreshed from files: preserve that behavior for in-cluster long-running processes.

{{{footer}}}
