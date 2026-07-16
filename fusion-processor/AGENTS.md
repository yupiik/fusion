<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-processor.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: Processor (`fusion-processor`)

Fusion annotation processor generating beans, JSON codecs, configuration binding and persistence models at build time.

The heart of the framework: a `javax.annotation.processing.Processor` which turns the `fusion-build-api`
annotations into reflectionless runtime code (beans, DI wiring, JSON codecs, configuration binding,
persistence entities, doc metadata).

## Entry points

- `io.yupiik.fusion.framework.processor.FusionProcessor`: the processor, registered through
  `src/main/resources/META-INF/services/javax.annotation.processing.Processor`.
- `internal/generator`: code generators (beans, listeners, subclasses/proxies).
- `internal/json`: JSON codec generation for `@JsonModel` records.
- `internal/persistence`: entity/table model generation.
- `internal/meta`: metadata emission, including `META-INF/fusion/configuration/documentation.json`
  (consumed by `fusion-documentation` and `fusion-build-internal`).

## Module rules

- ANY new annotation added to `fusion-build-api` must be handled here, plus documentation and tests.
- Generated code must stay reflectionless and readable: it is the runtime, users debug through it.
- Tests are compilation-based: see `FusionProcessorTest` (compiles fixture sources from
  `src/test/resources` and asserts on the generated output/behavior). Add a fixture per new feature.
- Supported annotations are processed per compilation unit: mind incremental compilation semantics
  when changing rounds handling.



## Working in this module

- Build it: `mvn install -pl fusion-processor -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

