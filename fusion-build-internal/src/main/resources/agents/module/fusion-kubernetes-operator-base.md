# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

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

{{{footer}}}
