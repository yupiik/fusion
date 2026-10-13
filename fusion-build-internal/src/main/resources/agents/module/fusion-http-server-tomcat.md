# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

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

{{{footer}}}