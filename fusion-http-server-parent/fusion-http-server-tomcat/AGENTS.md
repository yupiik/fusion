<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-http-server-tomcat.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: HTTP Server Tomcat (`fusion-http-server-tomcat`)

Apache Tomcat based implementation of the Fusion HTTP server (servlet adapter, server wiring and beans).

Apache Tomcat implementation of the Fusion HTTP server: the servlet adapter, the server wiring and the beans.

## Entry points

- `io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServer` / `TomcatWebServerConfiguration`: the server and its
  configuration.
- `io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerProvider`: the `WebServerProvider` implementation
  registered via `META-INF/services`, picked by the api static factories (`WebServer.of()`, `Configuration.of()`,
  `Cookie.of()`).
- `io.yupiik.fusion.http.server.impl.servlet.*`: servlet adapter (`FusionServlet`, `ServletRequest`, `ServletBody`, ...)
  and `ServletInputStreamSubscription`.
- `io.yupiik.fusion.http.server.impl.bean.*`: `FusionWebServerModule` and the server/observability beans
  (`FusionServerBean`, `FusionServerConfigurationBean`, `ObservabilityBeans`, ...).
- `io.yupiik.fusion.http.server.spi.MonitoringEndpoint` (from `fusion-http-server-api`): served by the monitoring
  connector for `/health` and `/metrics`.

## Module rules

- Tomcat (`tomcat-*` artifacts) is the ONLY third party runtime dependency family here; keep it that way.
- Response bodies use `java.util.concurrent.Flow` publishers: never block Tomcat threads in helpers.
- Tests run in parallel: always bind test servers to port `0` (random port), never a fixed port
  (the surefire config sets `fusion.http-server.port=0`).



## Working in this module

- Build it: `mvn install -pl fusion-http-server-parent/fusion-http-server-tomcat -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.
