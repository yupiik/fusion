# {{{name}}} (`{{{artifactId}}}`)

{{{description}}}

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

{{{footer}}}
