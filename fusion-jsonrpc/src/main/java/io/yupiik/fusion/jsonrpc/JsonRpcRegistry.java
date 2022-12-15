package io.yupiik.fusion.jsonrpc;

import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;

import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class JsonRpcRegistry {
    private final Map<String, JsonRpcMethod> methods;

    public JsonRpcRegistry(final List<JsonRpcMethod> methods) {
        this.methods = methods.stream().collect(toMap(JsonRpcMethod::name, identity(), (a, b) -> {
            if (a.priority() - b.priority() >= 0) {
                return a;
            }
            return b;
        }));
    }

    public Map<String, JsonRpcMethod> methods() {
        return methods;
    }
}
