package io.yupiik.fusion.http.server.impl.bean;

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.lifecycle.Start;
import io.yupiik.fusion.http.server.api.WebServer;

public class FusionServerStarterListener implements FusionListener<Start> {
    @Override
    public int priority() {
        return 2000;
    }

    @Override
    public Class<Start> eventType() {
        return Start.class;
    }

    @Override
    public void onEvent(final RuntimeContainer container, final Start event) {
        // close() in case but it is app scoped normally so no-op
        try (final var configuration = container.lookup(Configuration.class)) {
            if (!configuration.instance().get("fusion.http-server.start").map(Boolean::parseBoolean).orElse(true)) {
                return;
            }

            try (final var server = container.lookup(WebServer.class)) {
                server.instance(); // force a lookup to start it
            }
        }
    }
}
