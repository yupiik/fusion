# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

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

{{{footer}}}
