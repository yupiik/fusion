<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-handlebars.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: Handlebars (`fusion-handlebars`)

Handlebars templating engine implementation without any dependency.

Small handlebars-compatible template engine (each/if/unless/with, partials, helpers, `@first`/`@last`/`@index`
data variables) used for server-side rendering without any third party dependency.

## Entry points

- `io.yupiik.fusion.framework.handlebars.HandlebarsCompiler`: compile a template
  (`compile(new CompilationContext(content)).render(data)`); `Settings` carries helpers and partials.
- `io.yupiik.fusion.framework.handlebars.spi.Accessor` / `Template`: data access and rendering SPI.
- `io.yupiik.fusion.framework.handlebars.helper`: built-in helpers.

## Module rules

- Double-brace interpolation HTML-escapes, triple-brace renders raw: preserve that handlebars semantic.
- Data access goes through `Accessor` (maps/lists by default): no reflection on user objects.



## Working in this module

- Build it: `mvn install -pl fusion-handlebars -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

