package io.yupiik.fusion.jsonrpc.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.jsonrpc.JsonRpcRegistry;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;

import java.util.List;
import java.util.Map;

public class JsonRpcRegistryBean extends BaseBean<JsonRpcRegistry> {
    public JsonRpcRegistryBean() {
        super(JsonRpcRegistry.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public JsonRpcRegistry create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new JsonRpcRegistry(
                lookups(container, JsonRpcMethod.class, l -> l.stream().map(Instance::instance).toList(), dependents));
    }
}
