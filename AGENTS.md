<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/root.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Yupiik Fusion - agent guide

Fusion is a reflectionless, self-contained Java framework designed to be GraalVM-native friendly.
Business code never depends on jakarta/spring/guice. Everything heavy (bean/model discovery, DI wiring,
JSON codecs, configuration binding, persistence models, proxies) is generated at COMPILE TIME by an
annotation processor; only bean resolution happens at runtime.

- Documentation site: https://yupiik.github.io/fusion/
- Example applications: https://github.com/yupiik/fusion-examples

## Requirements and build commands

Requires Java >= 17 and Maven >= 3.8 (no wrapper is committed, use your own Maven).

| Command | Purpose |
|---|---|
| `mvn install` | Full build with tests (also regenerates all AGENTS.md/CLAUDE.md files). |
| `mvn install -pl <module> -am` | Build one module and its dependencies. |
| `mvn test -pl <module> -Dtest=MyTest` | Run a single test class. |
| `mvn license:format` | Add/fix the mandatory Apache-2.0 Yupiik license headers. |
| `mvn install -DskipTests ossindex:audit` | Dependency security audit. |
| `mvn compile yupiik-tools:serve-minisite -e` | Preview the documentation site (run in `fusion-documentation`). |

## Module map

Each module has its own `AGENTS.md` with entry points and module specific rules.

| Module | Description |
|---|---|
| [fusion-api](fusion-api/AGENTS.md) | Fusion runtime API: IoC container, scopes, lifecycle, events and configuration. |
| [fusion-processor](fusion-processor/AGENTS.md) | Fusion annotation processor generating beans, JSON codecs, configuration binding and persistence models at build time. |
| [fusion-build-api](fusion-build-api/AGENTS.md) | API which is used by the annotation processor but not the runtime. |
| [fusion-json](fusion-json/AGENTS.md) | Reflectionless JSON mapping with compile time generated codecs, JSON-Patch/Pointer/diff utilities. |
| [fusion-http-server](fusion-http-server/AGENTS.md) | HTTP server based on Apache Tomcat with a light request/response API and endpoint matchers. |
| [fusion-testing](fusion-testing/AGENTS.md) | JUnit 5 integration to test Fusion applications (container lifecycle, injection, CLI launcher). |
| [fusion-jsonrpc](fusion-jsonrpc/AGENTS.md) | JSON-RPC 2.0 server on top of the Fusion HTTP server with OpenRPC support. |
| [fusion-cli](fusion-cli/AGENTS.md) | Command line application support with compile time generated commands. |
| [fusion-httpclient-parent](fusion-httpclient-parent/AGENTS.md) | Parent of the Fusion HTTP client extensions. |
| [fusion-httpclient](fusion-httpclient-parent/fusion-httpclient/AGENTS.md) | Extended java.net.http client: routing, throttling, retries, logging listeners. |
| [fusion-kubernetes-client](fusion-httpclient-parent/fusion-kubernetes-client/AGENTS.md) | Lightweight Kubernetes client based on the JDK HTTP client (in-cluster and kubeconfig support). |
| [fusion-tracing](fusion-tracing/AGENTS.md) | Zipkin oriented tracing for the Fusion HTTP server and clients. |
| [fusion-observability](fusion-observability/AGENTS.md) | Observability HTTP server exposing health checks and OpenMetrics metrics. |
| [fusion-persistence](fusion-persistence/AGENTS.md) | Lightweight reflectionless JDBC persistence with compile time generated entity models. |
| [fusion-documentation](fusion-documentation/AGENTS.md) | AsciiDoc minisite of the project and documentation generators (configuration, OpenRPC converters). |
| [fusion-handlebars](fusion-handlebars/AGENTS.md) | Handlebars templating engine implementation without any dependency. |
| [fusion-jwt](fusion-jwt/AGENTS.md) | JWT signing and validation based on JDK cryptography. |
| [fusion-kubernetes-operator-base](fusion-kubernetes-operator-base/AGENTS.md) | Base to build Kubernetes operators with Fusion (watch/reconcile runtime, CRD support). |
| [fusion-build-internal](fusion-build-internal/AGENTS.md) | Internal build tooling keeping repository agent files (AGENTS.md/CLAUDE.md) in sync. Never published. |

How consumers use Fusion (the "triad"): applications depend on `fusion-build-api` + `fusion-processor`
in `provided` scope (compile time only) and `fusion-api` in `compile` scope (runtime). Opt-in modules
(json, http-server, jsonrpc, cli, persistence, ...) are added as needed.

## Design rules (do not break these)

- NO REFLECTION in runtime code paths: user code is bound through processor-generated classes, never
  `java.lang.reflect` lookups on business classes. This is the core promise of the framework.
- New user-facing annotations go in `fusion-build-api` (package `io.yupiik.fusion.framework.build.api.*`)
  and MUST be handled in `fusion-processor` (they are compile-time only, `provided` scope for consumers).
- Package conventions: public API in `...api`, extension points in `...spi`, implementation details in
  `...internal` or `...impl` (never referenced from user documentation or other modules' public API).
- There is deliberately NO declarative interceptor support - this is a documented design choice, do not add one.
- Keep modules dependency-light: no new third party runtime dependency without strong justification
  (Tomcat in `fusion-http-server` is the notable exception).

## Coding conventions

- Every `.java`, `.xml`, `.properties` and `.yaml` file starts with the Apache-2.0 Yupiik header
  (checked at `validate` phase, fix with `mvn license:format`).
- Java 17 level: records, switch expressions, `var` are idiomatic here; parameters and locals are
  typically `final`.
- ASCII-only content in sources and documentation.

## Testing conventions

- JUnit Jupiter, test classes are package-private, named `*Test`, mirroring the main package layout.
- Surefire runs tests IN PARALLEL (classes and methods, `junit.jupiter.execution.parallel.enabled=true`):
  tests must not rely on shared mutable global state, fixed network ports or the working directory.
- Use `fusion-testing` helpers: `@FusionSupport`/`@MonoFusionSupport` (container lifecycle), `@Fusion`
  (injection in tests), `@FusionCLITest` with `Stdout`/`Stderr` for CLI, `TestClient` for HTTP.

## Documentation workflow

- The site is AsciiDoc, sources in `fusion-documentation/src/main/minisite/content/fusion/*.adoc`.
- `fusion-documentation/src/main/minisite/content/_partials/generated/**` is GENERATED from the
  annotation metadata - never edit by hand.
- User-facing feature changes need a matching `.adoc` update.
- All `AGENTS.md` files and `CLAUDE.md` are GENERATED by `fusion-build-internal`: edit the templates in
  `fusion-build-internal/src/main/resources/agents/` then run `mvn install -pl fusion-build-internal`.
