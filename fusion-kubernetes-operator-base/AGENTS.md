<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-kubernetes-operator-base.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: K8s Operator Base (`fusion-kubernetes-operator-base`)

Base to build Kubernetes operators with Fusion (watch/reconcile runtime, CRD support).

Base to write Kubernetes operators with Fusion: watch/reconcile loop, CRD-typed events and awaiter, built on
`fusion-kubernetes-client`. Note the package root is `io.yupiik.kubernetes.operator.base` (not `io.yupiik.fusion`).

## Entry points

- `io.yupiik.kubernetes.operator.base.spi.Operator` / `BulkingOperator`: the SPI an operator implements.
- `io.yupiik.kubernetes.operator.base.impl.OperatorRuntime` / `OperatorsLifecycle`: watch/dispatch runtime.
- `io.yupiik.kubernetes.operator.base.impl.OperatorConfiguration`: runtime configuration.
- `@CustomResourceDefinition` (from `fusion-build-api`, `kubernetes/crd` package): generates the CRD JSON model.

## Module rules

- Must stay GraalVM native friendly (operators ship as small native images): no reflection, no dynamic loading.
- Reconciliation is event-driven and potentially concurrent: keep operator state handling thread-safe.

## Configuration properties

Generated from the annotation processor metadata (`META-INF/fusion/configuration/documentation.json`):

| Name | Env variable | Description | Default | Required |
|---|---|---|---|---|
| `operator.await` | `OPERATOR_AWAIT` | Should operator await process termination, keep it `true` until you embed it. | `true` | no |
| `operator.event-thread-count` | `OPERATOR_EVENT_THREAD_COUNT` | How many threads are handling events, take care that more than one require a specific concurrency handling. | `1` | no |
| `operator.kubernetes.certificates` | `OPERATOR_KUBERNETES_CERTIFICATES` | Kubernetes certificate to connect to its API. | `"/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"` | no |
| `operator.kubernetes.master` | `OPERATOR_KUBERNETES_MASTER` | The kubernetes API base URL. | `java.util.Optional.ofNullable(System.getenv("KUBERNETES_SERVICE_HOST")).map(host -> "https://" + host + ':' + java.util.Optional.ofNullable(System.getenv("KUBERNETES_SERVICE_PORT")).orElse("443")).orElse("https://kubernetes.default.svc")` | no |
| `operator.kubernetes.tls-skip` | `OPERATOR_KUBERNETES_TLS_SKIP` | Should TLS validations be skipped. | `false` | no |
| `operator.kubernetes.token` | `OPERATOR_KUBERNETES_TOKEN` | Kubernetes token (service account). | `"/var/run/secrets/kubernetes.io/serviceaccount/token"` | no |
| `operator.probe-port` | `OPERATOR_PROBE_PORT` | Server for healthchecks, set to a negative value to disable (when embedded for ex). | `8081` | no |
| `operator.storage` | `OPERATOR_STORAGE` | Operator can store locally (on the filesystem) the latest resource version it saw. This must be a directory, ignored if `null`. | - | no |
| `operator.use-bookmarks` | `OPERATOR_USE_BOOKMARKS` | If `true`, `BOOKMARK` events are enabled. | `true` | no |

## Working in this module

- Build it: `mvn install -pl fusion-kubernetes-operator-base -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

