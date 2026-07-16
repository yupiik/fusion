{{#if configuration}}
## Configuration properties

Generated from the annotation processor metadata (`META-INF/fusion/configuration/documentation.json`):

{{{configuration}}}
{{/if}}

## Working in this module

- Build it: `mvn install -pl {{{path}}} -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.
