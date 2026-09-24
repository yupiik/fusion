---
name: fusion-handlebars
description: Fusion `fusion-handlebars` - Handlebars templating engine implementation without any dependency. Use when working on or integrating this module, or
  when a task touches its annotations, entry points or configuration.
---

# fusion-handlebars

Handlebars templating engine implementation without any dependency.

# Fusion :: Handlebars (`fusion-handlebars`)

Handlebars templating engine implementation without any dependency.

Small handlebars-compatible template engine (each/if/unless/with, `{{else}}`/`{{else if}}` chains, hash arguments
like `{{#each people limit=2}}`, sub-expressions like `{{#if (eq a b)}}`, partials, custom helpers and a set of
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
- Blocks support `{{else}}` and `{{else if ...}}` chains; mismatched `{{/...}}` is a compile error.
- Helper args are parsed once at compile time (positionals, hash `key=value`, nested sub-expressions `(helper a b)`).
- Data access goes through `Accessor` (maps/lists by default): no reflection on user objects.



## Working in this module

- Build it: `mvn install -pl fusion-handlebars -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.



## Skill relationship

This is the module-specific skill. For the cross-cutting Fusion mechanics (dependency triad, compile-time
annotation processing, writing `@Command` CLI beans, testing helpers, documentation) load the corresponding
concept skills: `fusion-project-setup`, `fusion-annotation-processing`, `fusion-cli`, `fusion-testing`,
`fusion-documentation`.
<!-- generated from fusion-build-internal agents/skills/module/SKILL.md - do not edit -->
