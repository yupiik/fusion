# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

All user-facing annotations live here, under `io.yupiik.fusion.framework.build.api.*`. Consumers declare this
module in `provided` scope: it is only needed at compile time, the annotation processor (`fusion-processor`)
translates the annotations into generated code.

## API catalog (generated from the classpath)

{{{annotations}}}

## Module rules

- Compile-time only: NEVER make runtime code require this jar.
- Adding a type here without handling it in `fusion-processor` ships a no-op API: both always change together
  (plus documentation in `fusion-documentation`).
- Annotations must stay dependency-free (JDK only).

{{{footer}}}
