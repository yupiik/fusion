package io.yupiik.fusion.jsonrpc.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jsonrpc.JsonRpcEndpoint;
import io.yupiik.fusion.jsonrpc.JsonRpcHandler;

import java.util.List;
import java.util.Map;

public class JsonRpcEndpointBean extends BaseBean<JsonRpcEndpoint> {
    public JsonRpcEndpointBean() {
        super(JsonRpcEndpoint.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public JsonRpcEndpoint create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return new JsonRpcEndpoint(
                lookup(container, JsonRpcHandler.class, dependents),
                lookup(container, JsonMapper.class, dependents),
                // todo: could be read from configuration
                "/jsonrpc");
    }
}
