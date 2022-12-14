package io.yupiik.fusion.http.server.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.event.Emitter;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import io.yupiik.fusion.http.server.spi.Endpoint;

import java.util.List;
import java.util.Map;

// configuration as a bean to ensure it can be injected - at least to get the port
public class FusionServerConfigurationBean extends BaseBean<WebServer.Configuration> {
    public FusionServerConfigurationBean() {
        super(WebServer.Configuration.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public WebServer.Configuration create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final var configuration = WebServer.Configuration.of();
        try (final var conf = container.lookup(Configuration.class)) {
            final var confAccessor = conf.instance();
            confAccessor.get("fusion.http-server.port").map(Integer::parseInt).ifPresent(configuration::port);
            confAccessor.get("fusion.http-server.host").ifPresent(configuration::host);
            confAccessor.get("fusion.http-server.accessLogPattern").ifPresent(configuration::accessLogPattern);
            confAccessor.get("fusion.http-server.base").ifPresent(configuration::base);
            confAccessor.get("fusion.http-server.fusionServletMapping").ifPresent(configuration::fusionServletMapping);
            confAccessor.get("fusion.http-server.utf8Setup").map(Boolean::parseBoolean).ifPresent(configuration::utf8Setup);
        }

        configuration
                .unwrap(TomcatWebServerConfiguration.class)
                .setEndpoints(lookups(
                        container, Endpoint.class,
                        l -> l.stream().map(Instance::instance).toList(),
                        dependents));

        try (final var instance = container.lookup(Emitter.class)) { // enable a listener to customize the configuration
            instance.instance().emit(configuration);
        }

        return configuration;
    }
}
