package io.yupiik.fusion.jsonrpc.impl.bean;

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;

import java.util.stream.Stream;

public class JsonRpcModule implements FusionModule {
    @Override
    public Stream<FusionBean<?>> beans() {
        return Stream.of(
                new JsonRpcEndpointBean(),
                new JsonRpcHandlerBean(), new JsonRpcRegistryBean(),
                new ResponseJsonCodecBean(), new ErrorResponseJsonCodecBean());
    }
}
