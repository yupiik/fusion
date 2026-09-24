# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

Small handlebars-compatible template engine (each/if/unless/with, `{{{{h}}}}{{else}}{{{{/h}}}}`/`{{{{h}}}}{{else if}}{{{{/h}}}}` chains, hash arguments
like `{{{{h}}}}{{#each people limit=2}}{{{{/h}}}}`, sub-expressions like `{{{{h}}}}{{#if (eq a b)}}{{{{/h}}}}`, partials, custom helpers and a set of
built-in helpers `eq`/`ne`/`gt`/`gte`/`lt`/`lte`/`and`/`or`/`not`/`default`/`lookup`/`time`, plus
`@first`/`@last`/`@index` (and `@key`/`@value` for maps) data variables) used for server-side rendering without
any third party dependency.

## Entry points

- `io.yupiik.fusion.framework.handlebars.HandlebarsCompiler`: compile a template
  (`compile(new CompilationContext(content)).render(data)`); `Settings` carries helpers and partials.
- `io.yupiik.fusion.framework.handlebars.helper.HelperContext` and `helper.DefaultHelpers`: helper contract and built-in helpers.
- `io.yupiik.fusion.framework.handlebars.spi.Accessor` / `Template`: data access and rendering SPI.

## Module rules

- Double-brace interpolation HTML-escapes, triple-brace renders raw: preserve that handlebars semantic.
- Blocks support `{{{{h}}}}{{else}}{{{{/h}}}}` and `{{{{h}}}}{{else if ...}}{{{{/h}}}}` chains; mismatched `{{{{h}}}}{{/...}}{{{{/h}}}}` is a compile error.
- Helper args are parsed once at compile time (positionals, hash `key=value`, nested sub-expressions `(helper a b)`).
- Data access goes through `Accessor` (maps/lists by default): no reflection on user objects.

{{{footer}}}
