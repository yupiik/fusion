# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

Aggregator pom only, no code. See the children guides:

- [fusion-http-server-api](fusion-http-server-api/AGENTS.md): contracts (`Request`/`Response`/`WebServer`/`Endpoint`) and server-agnostic helpers.
- [fusion-http-server-observability](fusion-http-server-observability/AGENTS.md): health/metrics API and endpoints.
- [fusion-http-server-tomcat](fusion-http-server-tomcat/AGENTS.md): Tomcat implementation (servlet adapter, beans).
- [fusion-http-server](fusion-http-server/AGENTS.md): backward-compatible aggregator (api + observability + tomcat).
- [fusion-http-server-jre](fusion-http-server-jre/AGENTS.md): Tomcat-free JDK `com.sun.net.httpserver` adapter (api-only).

{{{footer}}}