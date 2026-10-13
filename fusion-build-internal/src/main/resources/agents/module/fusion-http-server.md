# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

Backward-compatible aggregator of the HTTP server modules. It pulls `fusion-http-server-api`,
`fusion-http-server-observability` and `fusion-http-server-tomcat` together so existing consumers keeping
only `io.yupiik.fusion:fusion-http-server` on the classpath get the full Tomcat-based server.

For lighter setups, depend on the fine-grained modules instead:

- `fusion-http-server-api`: contracts and helpers only (no server engine).
- `fusion-http-server-tomcat`: the Tomcat implementation.
- `fusion-http-server-jre`: a Tomcat-free JDK `com.sun.net.httpserver` adapter (api only).
- `fusion-http-server-observability`: health/metrics API and endpoints.

{{{footer}}}