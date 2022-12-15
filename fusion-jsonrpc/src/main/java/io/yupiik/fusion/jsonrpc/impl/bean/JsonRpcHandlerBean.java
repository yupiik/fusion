package io.yupiik.fusion.jsonrpc.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jsonrpc.JsonRpcHandler;
import io.yupiik.fusion.jsonrpc.JsonRpcRegistry;

import java.util.List;
import java.util.Map;

public class JsonRpcHandlerBean extends BaseBean<JsonRpcHandler> {
    public JsonRpcHandlerBean() {
        super(JsonRpcHandler.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public JsonRpcHandler create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new JsonRpcHandler(
                lookup(container, JsonMapper.class, dependents),
                lookup(container, JsonRpcRegistry.class, dependents));
    }
}
