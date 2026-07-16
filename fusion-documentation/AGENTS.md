<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-documentation.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: Documentation (`fusion-documentation`)

AsciiDoc minisite of the project and documentation generators (configuration, OpenRPC converters).

Two things live here: the AsciiDoc minisite (published to https://yupiik.github.io/fusion/) and the doc
generators run as `yupiik-tools-maven-plugin` pre-actions.

## Layout and entry points

- `src/main/minisite/content/fusion/*.adoc`: hand-written documentation pages (one per feature/module).
- `src/main/minisite/content/_partials/generated/`: GENERATED configuration docs - never edit by hand.
- `io.yupiik.fusion.documentation.ConfigurationDocumentationGenerator` (and legacy `DocumentationGenerator`):
  renders `META-INF/fusion/configuration/documentation.json` metadata to AsciiDoc.
- `io.yupiik.fusion.documentation.OpenRpcGenerator` / `OpenRPC2Adoc` / `OpenRPC2OpenAPI` / `OpenRPC2Postman`:
  JSON-RPC documentation converters.
- Preview the site locally: `mvn compile yupiik-tools:serve-minisite -e` (in this module).

## Module rules

- AsciiDoc conventions: tables with inline rows (a full row on one line when possible), `opts=headers` on
  tables, inline admonitions instead of blocks.
- Any user-facing feature change in other modules needs its `.adoc` page updated here.
- The default profile regenerates content on every build (`skipRendering=true`): commit the regenerated files.



## Working in this module

- Build it: `mvn install -pl fusion-documentation -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

