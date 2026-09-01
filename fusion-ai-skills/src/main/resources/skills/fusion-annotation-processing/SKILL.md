---
name: fusion-annotation-processing
description: How Fusion's annotation processor generates beans, JSON codecs, configuration binding, CLI commands, and persistence models at compile time from the annotations in fusion-build-api. Use when adding/modifying a user-facing "@" annotation, debugging or working in fusion-processor or fusion-build-api, or understanding how a business class becomes a processor-generated $FusionBean / XxxJsonCodec.
---

# Fusion annotation processing

Fusion does reflectionless, compile-time code generation. The `fusion-processor` module reads the
annotations from `fusion-build-api` and writes the wiring/JSON/config/persistence glue that a
reflection-based framework would resolve at runtime.

## The pipeline

1. Consumer code references annotations from `fusion-build-api` (scope `provided`).
2. `fusion-processor` is auto-registered as a `javax.annotation.processing.Processor` via
   `META-INF/services`, so it runs during `javac` with no explicit `-processorpath`.
3. The processor emits generated classes next to the source, dedicated to the annotated element.
4. At runtime only the tiny rebinding layer (`Launcher`, `Container`, `ArgsConfigSource`, ...) resolves
   the generated classes by name - always through processor-generated code, never `java.lang.reflect`.

## Where things live

- `fusion-build-api/.../build/api/*` - the compile-time annotations (the contract).
- `fusion-processor/src/main/java/io/yupiik/fusion/framework/processor/` - the generation logic.
- `fusion-processor/.../processor/internal/BaseGenerator.java` - base for the main generators.
- `fusion-processor/.../processor/internal/InternalFusionProcessor.java` - dispatch: maps each
  annotation to its generator(s) and orchestrates `merge`/`writeGeneratedClass`.

## What gets generated

| Concern            | Annotation(s)                          | Generated artifact(s)                    |
|--------------------|----------------------------------------|------------------------------------------|
| IoC bean           | `@Bean`, `@Scope`, `@Injection`        | `Xxx$FusionBean` (extends `BaseBean`)    |
| Configuration      | `@RootConfiguration`, `@Property`      | config binding + `documentation.json`    |
| JSON codec         | `@JsonCodec` / record/class detection  | `XxxJsonCodec` implementing `JsonCodec`  |
| CLI command        | `@Command` + `@RootConfiguration`      | command resolver wiring + `cli.json`     |
| Persistence entity | `@Entity` (... persistence annotations) | entity model (`DefaultBaseDatabase` glue) |

The generated code writes through `InternalFusionProcessor` `filer` API; `BaseGenerator` centralizes
header/license and class scaffolding. The processors enrich one shared model describing what to emit,
then each generator renders its artifact.

## Rule: `@Property.defaultValue` is a Java expression, not a raw string

The processor emits the default value VERBATIM into the generated `Xxx$FusionConfigurationFactory`. So use the
correct Java literal for the property type, exactly as it will be pasted into the generated code:

| Property type    | Correct default       | Wrong default        |
|------------------|-----------------------|----------------------|
| `String`         | `@Property(defaultValue = "\"local\"")` | `@Property(defaultValue = "local")` |
| `boolean`        | `@Property(defaultValue = "false")`  | `@Property(defaultValue = "\"false\"")` |
| `int`/`Integer`  | `@Property(defaultValue = "100")`   | `@Property(defaultValue = "100")` (fine) |

A String default written as `defaultValue = "local"` yields broken code: `configuration.get("scope").orElse(local)`.
When in doubt, mirror a known-good fixture such as `fusion-processor/src/test/resources/test/p/RecordConfiguration.java`
and `fusion-jwt/.../JwtValidatorConfiguration.java`.

## Rules when touching this area

- New user-facing annotation MUST go in `fusion-build-api` (`io.yupiik.fusion.framework.build.api.*`)
  and MUST be handled by `fusion-processor`. It is compile-time only (`provided` for consumers).
- Generated classes are regenerated on each build - never hand-edit `Xxx$FusionBean`, `XxxJsonCodec`,
  or `Xxx$Fusion` classes in `target/`.
- The annotation contract is single-sourced in `fusion-build-api`: change the annotation there first,
  then the matching generator in `fusion-processor`.
- Assertions/snapshot tests for generation live in `fusion-processor/src/test/` using the `Compiler`
  helper (see below).

## Testing generation

`fusion-processor/src/test/java/.../Compiler.java` compiles annotated fixtures and asserts the exact
generated sources. Add or update a fixture resource under `fusion-processor/src/test/resources/` when
changing generation behavior, then a test that runs `Compiler` and asserts the output. Run with
`mvn test -pl fusion-processor -Dtest=MyProcessorTest`.<!-- generated from fusion-build-internal src/main/resources/agents/skills/fusion-annotation-processing/SKILL.md - do not edit -->
