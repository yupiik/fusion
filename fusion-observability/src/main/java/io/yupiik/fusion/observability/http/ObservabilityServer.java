/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.fusion.observability.http;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.servlet.FusionServlet;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.AbstractProtocol;

import java.util.Set;
import java.util.stream.Stream;

import static io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServer.createBaseContext;

@ApplicationScoped
public class ObservabilityServer {
    private final Configuration configuration;
    private final MonitoringEndpointRegistry registry;
    private int port = -1;

    protected ObservabilityServer() {
        this(null, null);
    }

    public ObservabilityServer(final Configuration configuration, final MonitoringEndpointRegistry registry) {
        this.configuration = configuration;
        this.registry = registry;
    }

    public int getPort() {
        return port;
    }

    public void onWebServerConfiguration(@OnEvent final WebServer.Configuration configuration) {
        final var tomcatWebServerConfiguration = configuration.unwrap(TomcatWebServerConfiguration.class);
        final var customizers = tomcatWebServerConfiguration.getTomcatCustomizers();
        tomcatWebServerConfiguration.setTomcatCustomizers(
                Stream.concat(
                                customizers != null ? customizers.stream() : Stream.empty(),
                                Stream.of(t -> addObservabilityServer(t, tomcatWebServerConfiguration)))
                        .toList());
    }

    protected void addObservabilityServer(final Tomcat tomcat, final TomcatWebServerConfiguration webConf) {
        final var host = new StandardHost();
        host.setAutoDeploy(false);
        host.setName("localhost");
        host.addChild(newContext(webConf));

        final var engine = new StandardEngine();
        engine.setName("Monitoring");
        engine.setDefaultHost(host.getName());
        engine.addChild(host);

        final var connector = new Connector() {
            @Override
            protected void startInternal() throws LifecycleException {
                super.startInternal();
                if (getProtocolHandler() instanceof AbstractProtocol<?> ap) {
                    port = ap.getLocalPort();
                }
            }
        };
        connector.setPort(this.configuration.get("fusion.observability.server.port")
                .map(Integer::parseInt)
                .orElse(8181));

        final var service = new StandardService();
        service.setName("Observability");
        service.addConnector(connector);
        service.setContainer(engine);

        tomcat.getServer().addService(service);
    }

    protected Context newContext(final TomcatWebServerConfiguration webConf) {
        final var baseContext = createBaseContext(new TomcatWebServer.NoWorkDirContext(), webConf);
        baseContext.addServletContainerInitializer((ignored, ctx) -> {
            final var observability = ctx.addServlet("observability", new FusionServlet(registry.endpoints()));
            observability.setAsyncSupported(true);
            observability.setLoadOnStartup(1);
            observability.addMapping("/*");
        }, Set.of());
        return baseContext;
    }
}
