---
name: fusion-project-setup
description: How a Fusion application/consumer project is wired up (the "triad" of fusion-build-api + fusion-processor in provided scope and fusion-api in compile scope), module conventions (license headers, package layout, ASCII-only), and the build/license/test commands. Use when adding a module, configuring Maven dependencies for a Fusion project, or running the Fusion build.
---

# Fusion project setup

Fusion is a reflectionless, self-contained Java framework (GraalVM-native friendly).
Business code never depends on jakarta/spring/guice; everything heavy is generated at COMPILE TIME by an
annotation processor and only bean resolution happens at runtime.

## The dependency triad

A Fusion consumer project declares three dependencies:

| Artifact        | Scope     | Purpose                                           |
|-----------------|-----------|---------------------------------------------------|
| `fusion-build-api`   | provided  | Compile-time annotations (`@Bean`, `@RootConfiguration`, `@Command`, ...) |
| `fusion-processor`   | provided  | Annotation processor (auto-registered via `META-INF/services`, no explicit `-processorpath`) |
| `fusion-api`         | compile   | Runtime API (IoC container, scopes, lifecycle, events, configuration, `Launcher`/`CliLauncher`) |

Opt-in modules are added as needed at runtime: `fusion-json`, `fusion-http-server`, `fusion-jsonrpc`,
`fusion-cli`, `fusion-persistence`, `fusion-handlebars`, `fusion-jwt`, ...

## Module conventions (do not break these)

- NO REFLECTION in runtime code paths: user code is bound through processor-generated classes, never
  `java.lang.reflect` lookups on business classes.
- New user-facing annotations go in `fusion-build-api` (package `io.yupiik.fusion.framework.build.api.*`)
  and MUST be handled in `fusion-processor`.
- Package conventions: public API in `...api`, extension points in `...spi`, implementation in
  `...internal` or `...impl`.
- No declarative interceptor support (documented design choice).
- Keep modules dependency-light: no new third party runtime dependency without strong justification.

## Coding conventions

- Every `.java`, `.xml`, `.properties`, `.yaml` file starts with the Apache-2.0 Yupiik header
  (fixed with `mvn license:format`).
- Java 17 level: records, switch expressions, `var` are idiomatic; `final` params/locals.
- ASCII-only content in sources and documentation.
- Always use braces for control-flow blocks.

## Build and test commands

| Command | Purpose |
|---|---|
| `mvn install` | Full build with tests (also regenerates all AGENTS.md/CLAUDE.md files). |
| `mvn install -pl <module> -am` | Build one module and its dependencies. |
| `mvn test -pl <module> -Dtest=MyTest` | Run a single test class. |
| `mvn license:format` | Add/fix the mandatory Apache-2.0 Yupiik license headers. |
| `mvn install -DskipTests ossindex:audit` | Dependency security audit. |

## Testing conventions

- JUnit Jupiter, package-private classes named `*Test`, mirroring the main package layout.
- Surefire runs tests IN PARALLEL: no shared mutable global state, fixed network ports, or working
  directory dependence.
- Use `fusion-testing` helpers: `@FusionSupport`/`@MonoFusionSupport`, `@Fusion` (injection),
  `@FusionCLITest` with `Stdout`/`Stderr`, `TestClient` for HTTP.

## Documentation

The site is AsciiDoc in `fusion-documentation/src/main/minisite/content/fusion/*.adoc`.
User-facing feature changes need a matching `.adoc` update. All `AGENTS.md`/`CLAUDE.md` are GENERATED
by `fusion-build-internal` - edit templates in `fusion-build-internal/src/main/resources/agents/` and
run `mvn install -pl fusion-build-internal`.