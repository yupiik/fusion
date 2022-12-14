package io.yupiik.fusion.http.server.impl.bean;

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;

import java.util.stream.Stream;

public class FusionWebServerModule implements FusionModule {
    @Override
    public Stream<FusionBean<?>> beans() {
        return Stream.of(new FusionServerConfigurationBean(), new FusionServerBean(), new FusionServerAwaiterBean());
    }

    @Override
    public Stream<FusionListener<?>> listeners() {
        return Stream.of(new FusionServerStarterListener());
    }
}
