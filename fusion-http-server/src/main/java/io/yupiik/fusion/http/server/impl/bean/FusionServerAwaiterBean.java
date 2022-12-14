package io.yupiik.fusion.http.server.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.main.Awaiter;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.http.server.api.WebServer;

import java.util.List;
import java.util.Map;

public class FusionServerAwaiterBean extends BaseBean<Awaiter> {
    public FusionServerAwaiterBean() {
        super(Awaiter.class, DefaultScoped.class, 1000, Map.of());
    }

    @Override
    public Awaiter create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return () -> {
            try (final var server = container.lookup(WebServer.class)) {
                server.instance().await();
            }
        };
    }
}
