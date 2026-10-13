<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-http-server-parent.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: HTTP Server Parent (`fusion-http-server-parent`)

Parent of the HTTP server modules (api, observability, tomcat, jre and the backward-compatible aggregator).

Aggregator pom only, no code. See the children guides:

- [fusion-http-server-api](fusion-http-server-api/AGENTS.md): contracts (`Request`/`Response`/`WebServer`/`Endpoint`) and server-agnostic helpers.
- [fusion-http-server-observability](fusion-http-server-observability/AGENTS.md): health/metrics API and endpoints.
- [fusion-http-server-tomcat](fusion-http-server-tomcat/AGENTS.md): Tomcat implementation (servlet adapter, beans).
- [fusion-http-server](fusion-http-server/AGENTS.md): backward-compatible aggregator (api + observability + tomcat).
- [fusion-http-server-jre](fusion-http-server-jre/AGENTS.md): Tomcat-free JDK `com.sun.net.httpserver` adapter (api-only).



## Working in this module

- Build it: `mvn install -pl fusion-http-server-parent -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.
