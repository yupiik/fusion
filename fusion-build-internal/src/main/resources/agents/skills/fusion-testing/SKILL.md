---
name: fusion-testing
description: Fusion's testing conventions and helpers for container lifecycle, injection, CLI and HTTP tests: @FusionSupport/@MonoFusionSupport, @Fusion, @FusionCLITest with Stdout/Stderr, TestClient. Use when writing or debugging tests for a Fusion module/application, including the parallel-execution constraints.
---

# Fusion testing

JUnit Jupiter. Test classes are package-private, named `*Test`, mirroring the main package layout.

## Parallel execution constraint

Surefire runs tests IN PARALLEL (classes and methods,
`junit.jupiter.execution.parallel.enabled=true`). Tests MUST NOT rely on shared mutable global state,
fixed network ports, or the working directory.

## Helpers (fusion-testing)

| Helper | Purpose |
|---|---|
| `@FusionSupport` / `@MonoFusionSupport` | Container lifecycle around a test (start/stop). |
| `@Fusion` | Inject beans into the test. |
| `@FusionCLITest` with `Stdout` / `Stderr` | Run CLI tests, capture output streams. |
| `TestClient` | HTTP test client against a started server. |
| `Task` | Captures and asserts on collected data (its `Supplier` is widely used). |

## CLI command tests

Commands are beans. Assert resolution and output via `CliCommandResolver` and `Launcher`/`CliLauncher`
from `fusion-cli`. For a module that declares commands, use `@FusionCLITest` to exercise the end-to-end
command pipeline with `Stdout`/`Stderr` to verify what is printed.

## HTTP tests

Start the server under test with `@FusionSupport`/`@MonoFusionSupport`, then exercise it with
`TestClient` (see `fusion-http-server/src/test`).

## Run them

- One test class: `mvn test -pl <module> -Dtest=MyTest`
- Whole module: `mvn install -pl <module> -am`

When adding a test that exercises compile-time generation (e.g. a new annotation), also add a
generation assertion in `fusion-processor` (see the `fusion-annotation-processing` skill).