<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-build-api.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: Build API (`fusion-build-api`)

API which is used by the annotation processor but not the runtime.

All user-facing annotations live here, under `io.yupiik.fusion.framework.build.api.*`. Consumers declare this
module in `provided` scope: it is only needed at compile time, the annotation processor (`fusion-processor`)
translates the annotations into generated code.

## API catalog (generated from the classpath)

- `cli`: `@Command`
- `configuration`: `@ConfigurationModel`, `@Property`, `@RootConfiguration`
- `container`: `@DetectableContext`, `@LazyContext`
- `event`: `@OnEvent`
- `http`: `@HttpJavaMatcher`, `@HttpMatcher`
- `json`: `@JsonModel`, `@JsonOthers`, `@JsonProperty`
- `jsonrpc`: `@JsonRpc`, `@JsonRpcError`, `@JsonRpcParam`
- `kubernetes.crd`: `@CustomResourceDefinition`
- `lifecycle`: `@Destroy`, `@Init`
- `metadata`: `@BeanMetadata`, `@BeanMetadataAlias`
- `metadata.spi`: `MetadataContributor`
- `order`: `@Order`
- `persistence`: `@Column`, `@Id`, `@OnDelete`, `@OnInsert`, `@OnLoad`, `@OnUpdate`, `@Table`
- `scanning`: `@Bean`, `@Injection`

## Module rules

- Compile-time only: NEVER make runtime code require this jar.
- Adding a type here without handling it in `fusion-processor` ships a no-op API: both always change together
  (plus documentation in `fusion-documentation`).
- Annotations must stay dependency-free (JDK only).



## Working in this module

- Build it: `mvn install -pl fusion-build-api -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

