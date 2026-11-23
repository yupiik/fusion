---
name: fusion-annotation-processing
description: How Fusion's annotation processor generates compact bean, JSON, configuration, CLI, and persistence bindings at compile time from fusion-build-api annotations. Use when changing annotations, debugging fusion-processor or fusion-build-api, or understanding generated module wiring and typed construction methods.
---

# Fusion annotation processing

Fusion does reflectionless, compile-time code generation. The `fusion-processor` module reads the
annotations from `fusion-build-api` and writes the wiring/JSON/config/persistence glue that a
reflection-based framework would resolve at runtime.

## The pipeline

1. Consumer code references annotations from `fusion-build-api` (scope `provided`).
2. `fusion-processor` is auto-registered as a `javax.annotation.processing.Processor` via
   `META-INF/services`, so it runs during `javac` with no explicit `-processorpath`.
3. The processor emits compact module/package binding holders backed by shared runtime adapters,
   rather than one implementation class per annotated element.
4. At runtime generated modules register beans and listeners through container discovery, without
   reflective lookups on business classes.

## Where things live

- `fusion-build-api/.../build/api/*` - the compile-time annotations (the contract).
- `fusion-processor/src/main/java/io/yupiik/fusion/framework/processor/` - the generation logic.
- `fusion-processor/.../processor/internal/BaseGenerator.java` - base for the main generators.
- `fusion-processor/.../processor/internal/InternalFusionProcessor.java` - dispatch: maps each
  annotation to its generator(s) and orchestrates `merge`/`writeGeneratedClass`.

## What gets generated

| Concern            | Annotation(s)                          | Generated artifact(s)                    |
|--------------------|----------------------------------------|------------------------------------------|
| IoC bean           | `@Bean`, scopes, `@Injection`         | module registrations using runtime adapters |
| Configuration      | `@RootConfiguration`, `@Property`      | config binding + `documentation.json`    |
| JSON codec         | `@JsonModel`                          | typed `JsonCodec` construction method    |
| CLI command        | `@Command` + `@RootConfiguration`      | command resolver wiring + `cli.json`     |
| Persistence entity | `@Entity` (... persistence annotations) | entity model (`DefaultBaseDatabase` glue) |

The generated code writes through `InternalFusionProcessor` `filer` API; `BaseGenerator` centralizes
header/license and class scaffolding. The processors enrich one shared model describing what to emit,
then each generator renders its artifact.

## Stable manual construction in 1.3

Normally let the container discover bindings, or let `JsonMapper` discover registered codecs.
For standalone construction, keep module generation enabled and configure
`-Afusion.moduleFqn=com.acme.FusionModule`, with the root module in the model's package.
Call its public static, typed method: `FusionModule.Model$FusionJsonCodec()` or
`FusionModule.Settings$FusionConfigurationFactory()` (returns a typed `BiFunction` to apply to
`Configuration` and a diagnostic key mapper).
Nested type names retain `$`, such as `FusionModule.Outer$Model$FusionJsonCodec()`.
These root-module methods remain available when size overflow moves their implementation to holders.
Do not depend on `FusionBindings_*` names, shard numbers, or removed per-model classes such as
`Model$FusionJsonCodec`, `Settings$FusionConfigurationFactory`, and `Service$FusionBean`.
Models outside the root module's package and builds with `fusion.generateModule=false` retain
standalone construction owners, but their generated owner names are not a stable public entry point.
Ordinary bean/listener descriptors have no standalone factory; use generated module registration.
See https://www.yupiik.io/fusion/tooling.html#manual-binding-lookup and
https://www.yupiik.io/fusion/migration-1.3.html for supported return types and migration details.

## Rule: `@Property.defaultValue` is a Java expression, not a raw string

The processor emits the default value VERBATIM into generated configuration binding code. So use the
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
- Generated sources are regenerated on each build - never hand-edit module or binding holders in `target/`.
- The annotation contract is single-sourced in `fusion-build-api`: change the annotation there first,
  then the matching generator in `fusion-processor`.
- Assertions/snapshot tests for generation live in `fusion-processor/src/test/` using the `Compiler`
  helper (see below).

## Testing generation

`fusion-processor/src/test/java/.../Compiler.java` compiles annotated fixtures and asserts the exact
generated sources. Add or update a fixture resource under `fusion-processor/src/test/resources/` when
changing generation behavior, then a test that runs `Compiler` and asserts the output. Run with
`mvn test -pl fusion-processor -Dtest=MyProcessorTest`.
