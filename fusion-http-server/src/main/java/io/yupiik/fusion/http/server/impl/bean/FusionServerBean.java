package io.yupiik.fusion.http.server.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.http.server.api.WebServer;

import java.util.List;
import java.util.Map;

public class FusionServerBean extends BaseBean<WebServer> {
    public FusionServerBean() {
        super(WebServer.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public WebServer create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return WebServer.of(lookup(container, WebServer.Configuration.class, dependents));
    }

    @Override
    public void destroy(final RuntimeContainer container, final WebServer instance) {
        instance.close();
    }
}
