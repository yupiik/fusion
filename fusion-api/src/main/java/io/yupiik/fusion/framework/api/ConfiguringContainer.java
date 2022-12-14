package io.yupiik.fusion.framework.api;

import io.yupiik.fusion.framework.api.container.ContainerImpl;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.spi.FusionContext;

public interface ConfiguringContainer {
    static ConfiguringContainer of() {
        return new ContainerImpl();
    }

    RuntimeContainer start();

    ConfiguringContainer disableAutoDiscovery(boolean disableAutoDiscovery);

    ConfiguringContainer loader(ClassLoader loader);

    ConfiguringContainer register(FusionBean<?>... beans);

    ConfiguringContainer register(FusionListener<?>... listeners);

    ConfiguringContainer register(FusionContext... contexts);
}
