<!--
  GENERATED FILE - DO NOT EDIT.
  Template: fusion-build-internal/src/main/resources/agents/module/fusion-jsonrpc.md
  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).
-->
# Fusion :: JSON-RPC (`fusion-jsonrpc`)

JSON-RPC 2.0 server on top of the Fusion HTTP server with OpenRPC support.

Methods annotated with `@JsonRpc` (from `fusion-build-api`) become JSON-RPC 2.0 methods served over the
Fusion HTTP server (single endpoint, default `/jsonrpc`).

```java
public class Endpoints { // a bean, methods can be sync or return a CompletionStage
    @JsonRpc("copy")
    public MyResult result(final MyInput input) { // parameters/results are @JsonModel records
        return new MyResult(input.name());
    }

    @JsonModel
    public record MyInput(String name) {}

    @JsonModel
    public record MyResult(String name) {}
}
```

## Entry points

- `io.yupiik.fusion.jsonrpc.JsonRpcEndpoint`: the HTTP endpoint dispatching requests.
- `io.yupiik.fusion.jsonrpc.JsonRpcHandler` / `JsonRpcRegistry`: method resolution and execution.
- `io.yupiik.fusion.jsonrpc.JsonRpcException` / `Response`: error and response modeling.
- `io.yupiik.fusion.jsonrpc.bean.OpenRPCEndpoint` / `OpenRPCHttpEndpoint`: openrpc.json exposure of the registered methods.
- `io.yupiik.fusion.jsonrpc.event.BeforeRequest`: hook event fired before method execution (validation/auth).

## Module rules

- Protocol behavior (error codes, batch handling) follows JSON-RPC 2.0: check the spec before changing responses.
- The OpenRPC document is generated from the processor metadata: keep it consistent when touching method binding.



## Working in this module

- Build it: `mvn install -pl fusion-jsonrpc -am` (from the repository root).
- The root [AGENTS.md](/AGENTS.md) holds the global rules: reflectionless design, license headers, parallel JUnit 5 tests, ASCII-only.

