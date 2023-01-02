package test.p;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.framework.build.api.lifecycle.Init;
import io.yupiik.fusion.framework.build.api.scanning.Bean;
import io.yupiik.fusion.framework.build.api.scanning.Injection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;

public interface NestedJsonRpc {
    public static class Rpc {
        @JsonRpc("test2")
        public CompletionStage<MyResult> asynResult(final MyInput in) {
            return completedFuture(new MyResult(in.name()));
        }
    }

    @JsonModel
    public record MyResult(String name) {
    }

    @JsonModel
    public record MyInput(String name) {
    }
}