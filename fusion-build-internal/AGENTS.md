<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-build-internal.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: Build Internal (`fusion-build-internal`)

Internal build tooling keeping repository agent files (AGENTS.md/CLAUDE.md) in sync. Never published.

This module generates the file you are reading: all `AGENTS.md` files and the root `CLAUDE.md` are rendered
from handlebars templates at `process-classes` phase (exec-maven-plugin) and committed to git.

## Layout and entry points

- `io.yupiik.fusion.build.internal.agent.AgentsFileSynchronizer`: the generator (also a plain `main`).
- `src/main/resources/agents/root.md` and `agents/claude.md`: root `AGENTS.md`/`CLAUDE.md` templates.
- `src/main/resources/agents/module/<artifactId>.md`: per-module templates (`_default.md` fallback,
  `_footer.md` shared tail).
- Data available in templates: pom data (module list, names, descriptions, java release), per-module
  configuration tables from `META-INF/fusion/configuration/documentation.json`, and the `fusion-build-api`
  annotation catalog.

## Module rules

- To change any AGENTS.md/CLAUDE.md: edit the template, then run `mvn install -pl fusion-build-internal`
  and commit both the template and the regenerated files.
- `-Dfusion.build-internal.agents.mode=check` makes the build fail on stale files instead of rewriting them (CI friendly).
- This module is never published (deploy and central publishing are skipped).
- When a module is added to the reactor, add its template here (the build warns and falls back to `_default.md`).



## Working in this module

- Build it: `mvn install -pl fusion-build-internal -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

