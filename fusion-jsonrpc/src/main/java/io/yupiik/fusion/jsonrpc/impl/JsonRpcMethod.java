package io.yupiik.fusion.jsonrpc.impl;

import io.yupiik.fusion.http.server.api.Request;

import java.util.concurrent.CompletionStage;

public interface JsonRpcMethod {
    default int priority() {
        return 1000;
    }

    String name();

    CompletionStage<?> invoke(Context context);

    record Context(Request request, Object params) {
    }
}
