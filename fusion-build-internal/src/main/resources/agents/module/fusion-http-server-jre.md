# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

Tomcat-free adapter running the Fusion HTTP server endpoints (`Endpoint`/`Request`/`Response`) on top of the JDK
`com.sun.net.httpserver.HttpServer`. Depends only on `fusion-http-server-api`, so it purposely avoids loading any
Tomcat/servlet class.

## Entry points

- `io.yupiik.fusion.httpserver.jre.JreHttpServer`: the adapter (binds a JDK `HttpServer`, matches endpoints by
  priority, streams SSE-style bodies).
- `io.yupiik.fusion.httpserver.jre.JreHttpServerConfiguration`: plain configuration (host, port, virtual threads).
- `io.yupiik.fusion.httpserver.jre.JreRequest`: `Request`/`Body` view over an `HttpExchange`.

## Module rules

- Depends only on `fusion-http-server-api` (plus `fusion-api` transitively): keep no `jakarta.servlet`/(Tomcat)
  dependency.
- Java 17 compatible: virtual-thread executors are used only when the JDK supports them (fallback to a cached
  platform thread pool otherwise).
- Dispatch and body writing never block the `HttpServer` accept thread.

{{{footer}}}