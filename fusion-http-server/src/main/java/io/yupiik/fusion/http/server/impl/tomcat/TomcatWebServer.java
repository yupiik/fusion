/*
 * Copyright (c) 2022 - present - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.http.server.impl.tomcat;

import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.servlet.FusionServlet;
import io.yupiik.fusion.http.server.spi.MonitoringEndpoint;
import jakarta.servlet.annotation.HandlesTypes;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.apache.catalina.valves.AbstractAccessLogValve;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.AbstractProtocol;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.SimpleInstanceManager;
import org.apache.tomcat.util.modeler.Registry;

import java.io.CharArrayWriter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

import static java.util.Optional.ofNullable;

// inspired from @apache/openwebbeans-meecrowave and @yupiik/uship
public class TomcatWebServer implements WebServer {
    private static final boolean HAS_JAKARTA_ANNOTATIONS;

    static {
        boolean hasJakartaAnnotations;
        try {
            Class.forName("jakarta.annotation.PostConstruct");
            hasJakartaAnnotations = true;
        } catch (final ClassNotFoundException cnfe) {
            hasJakartaAnnotations = false;
        }
        HAS_JAKARTA_ANNOTATIONS = hasJakartaAnnotations;
    }

    protected final TomcatWebServerConfiguration configuration;
    protected final Tomcat tomcat;

    public TomcatWebServer(final TomcatWebServerConfiguration configuration) {
        this.configuration = configuration;

        if (configuration.isDisableRegistry()) {
            Registry.disableRegistry();
        }

        tomcat = createTomcat();

        final var context = createContext();
        tomcat.getHost().addChild(context);

        final var state = context.getState();
        if (state == LifecycleState.STOPPED || state == LifecycleState.FAILED) {
            try {
                close();
            } catch (final RuntimeException re) {
                // no-op
            }
            throw new IllegalStateException("Context didn't start");
        }
        if (configuration.getPort() == 0) {
            configuration.setPort(getPort());
        }
    }

    public Tomcat tomcat() {
        return tomcat;
    }

    public int getPort() {
        return ((AbstractProtocol<?>) tomcat.getConnector().getProtocolHandler()).getLocalPort();
    }

    @Override
    public synchronized void close() {
        if (tomcat == null) {
            return;
        }
        try {
            final var server = tomcat.getServer();
            tomcat.stop();
            tomcat.destroy();
            if (server != null) { // give a change to stop the utility executor otherwise it just leaks and stop later
                final var utilityExecutor = server.getUtilityExecutor();
                if (utilityExecutor != null) {
                    try {
                        if (!utilityExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
                            LogFactory.getLog(getClass()).warn("Can't stop tomcat utility executor in 1mn, giving up");
                        }
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (final LifecycleException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Override
    public void await() {
        tomcat.getServer().await();
    }

    protected Tomcat createTomcat() {
        final var tomcat = newTomcat();
        tomcat.setBaseDir(configuration.getBase());
        tomcat.setPort(configuration.getPort());

        final var host = new StandardHost();
        host.setAutoDeploy(false);
        // note needed to stick to tomcat but neat to enable in customizers: host.setFailCtxIfServletStartFails(true);
        host.setName(configuration.getDefaultHost());
        tomcat.getEngine().addChild(host);

        if (configuration.getCompression() != null && !configuration.getCompression().isBlank()) {
            tomcat.getConnector().setProperty("compression", configuration.getCompression());
        }

        if (configuration.getMonitoringServerConfiguration() != null) {
            addMonitoring(tomcat);
        }

        if (configuration.getTomcatCustomizers() != null) {
            configuration.getTomcatCustomizers().forEach(c -> c.accept(tomcat));
        }

        onTomcat(tomcat);

        try {
            tomcat.init();
        } catch (final LifecycleException e) {
            try {
                tomcat.destroy();
            } catch (final LifecycleException ex) {
                // no-op
            }
            throw new IllegalStateException(e);
        }
        try {
            tomcat.start();
        } catch (final LifecycleException e) {
            close();
            throw new IllegalStateException(e);
        }
        return tomcat;
    }

    private void addMonitoring(final Tomcat tomcat) {
        // do not recreate a tomcat tree, reuse the main one, just add a monitoring context and a connector
        final var tomcatServer = tomcat.getServer();

        final var tmpContextConf = new TomcatWebServerConfiguration();
        tmpContextConf.setAccessLogPattern(null); // skip access log completely

        final var context = createBaseContext(new TomcatWebServer.NoWorkDirContext(), tmpContextConf);
        context.setName("monitoring");
        context.setPath("");
        context.addServletContainerInitializer((i, c) -> {
            final var endpoints = configuration.getMonitoringServerConfiguration().getEndpoints() == null ?
                    List.<MonitoringEndpoint>of() : configuration.getMonitoringServerConfiguration().getEndpoints();
            final var instance = c.addServlet("fusion-monitoring", new FusionServlet(endpoints));
            instance.setLoadOnStartup(1);
            instance.setAsyncSupported(true);
            instance.addMapping("/");
        }, Set.of());

        final var host = new StandardHost();
        host.setAutoDeploy(false);
        host.setName("localhost");
        host.addChild(context);

        final var engine = new StandardEngine();
        engine.setName("Monitoring");
        engine.setDefaultHost(host.getName());
        engine.addChild(host);

        final var connector = new CapturingPortConnector(p -> {
            if (configuration.getMonitoringServerConfiguration().getPort() == 0) {
                configuration.getMonitoringServerConfiguration().setPort(p);
            }
        });
        connector.setPort(configuration.getMonitoringServerConfiguration().getPort());

        final var service = new StandardService();
        service.setName("Monitoring");
        service.addConnector(connector);
        service.setContainer(engine);

        tomcatServer.addService(service);
    }

    protected StandardContext createContext() {
        final var ctx = createBaseContext(newContext(), configuration);

        if (!configuration.getEndpoints().isEmpty() &&
                configuration.getFusionServletMapping() != null &&
                !"-".equals(configuration.getFusionServletMapping())) {
            ctx.addServletContainerInitializer((ignored, servletContext) -> {
                final var fusion = servletContext.addServlet("fusion", new FusionServlet(configuration.getEndpoints()));
                fusion.setAsyncSupported(true);
                fusion.setLoadOnStartup(1);
                fusion.addMapping(configuration.getFusionServletMapping());
            }, Set.of());
        }

        configuration.getInitializers().forEach(sci -> ctx.addServletContainerInitializer(
                sci, ofNullable(sci.getClass().getAnnotation(HandlesTypes.class))
                        .map(HandlesTypes::value)
                        .map(this::scanFor)
                        .orElseGet(Set::of)));

        if (configuration.getContextCustomizers() != null) {
            configuration.getContextCustomizers().forEach(c -> c.accept(ctx));
        }

        onContext(ctx);

        return ctx;
    }

    public static StandardContext createBaseContext(final StandardContext ctx,
                                                    final TomcatWebServerConfiguration configuration) {
        ctx.setLoader(new LaunchingClassLoaderLoader());
        ctx.setPath("");
        ctx.setName("");
        ctx.setFailCtxIfServletStartFails(true);
        // ctx.setJarScanner(newSkipScanner()); // we don't use scanning at all with this setup so just ignore useless optims for now

        if (!configuration.isSkipUtf8Setup()) {
            ctx.setRequestCharacterEncoding("UTF-8");
            ctx.setResponseCharacterEncoding("UTF-8");
        }

        ctx.addLifecycleListener(new Tomcat.FixContextListener());

        final var errorReportValve = new ErrorReportValve();
        errorReportValve.setShowReport(false);
        errorReportValve.setShowServerInfo(false);

        if (configuration.getAccessLogPattern() != null && !configuration.getAccessLogPattern().isBlank()) {
            final var logValve = new JULAccessLogValve();
            logValve.setPattern(configuration.getAccessLogPattern());
            if (configuration.getSkipAccessLogAttribute() != null && !configuration.getSkipAccessLogAttribute().isBlank()) {
                logValve.setCondition(configuration.getSkipAccessLogAttribute());
            }
            ctx.getPipeline().addValve(logValve);
        }

        ctx.getPipeline().addValve(errorReportValve);

        if (configuration.isFastSessionId()) {
            final var mgr = new StandardManager();
            mgr.setSessionIdGenerator(new FastSessionIdGenerator());
            ctx.setManager(mgr);
        }

        if (!HAS_JAKARTA_ANNOTATIONS) {
            ctx.setInstanceManager(new SimpleInstanceManager());
        }

        // no need of all these checks in general since we use a flat classpath
        ctx.setClearReferencesThreadLocals(false);
        ctx.setClearReferencesRmiTargets(false);
        ctx.setClearReferencesHttpClientKeepAliveThread(false);
        ctx.setClearReferencesStopThreads(false);
        ctx.setClearReferencesStopTimerThreads(false);
        ctx.setSkipMemoryLeakChecksOnJvmShutdown(true);
        ctx.setRenewThreadsWhenStoppingContext(false);

        return ctx;
    }

    protected Tomcat newTomcat() {
        return new NoBaseDirTomcat();
    }

    protected StandardContext newContext() {
        return new NoWorkDirContext();
    }

    protected void onTomcat(final Tomcat tomcat) {
        // no-op
    }

    protected void onContext(final StandardContext context) {
        // no-op
    }

    // default does not scan anything but can be overriden if relevant
    protected Set<Class<?>> scanFor(final Class<?>... classes) {
        return Set.of();
    }

    public static class JULAccessLogValve extends AbstractAccessLogValve {
        private final Logger logger = Logger.getLogger("fusion.webserver.tomcat.access.log");
        private BiConsumer<Request, Runnable> logWrapper;

        /**
         * Very useful to get from the request (attributes) some context to initialize while logger output data.
         * This is commonly used with a custom logger appender/handler or formatter to log contextual data like traceId.
         *
         * @param logWrapper consumer which will setup the context for this request logging.
         * @return this.
         */
        public JULAccessLogValve setLogWrapper(final BiConsumer<Request, Runnable> logWrapper) {
            this.logWrapper = logWrapper;
            return this;
        }

        @Override
        public void log(final Request request, final Response response, final long time) {
            if (logWrapper == null) {
                super.log(request, response, time);
            } else {
                logWrapper.accept(request, () -> super.log(request, response, time));
            }
        }

        @Override
        protected void log(final CharArrayWriter message) {
            logger.info(message.toString());
        }
    }

    public static class NoBaseDirTomcat extends Tomcat {
        @Override
        protected void initBaseDir() {
            // no-op
        }
    }

    public static class NoWorkDirContext extends StandardContext {
        @Override
        protected void postWorkDirectory() {
            // no-op
        }
    }

    public static class FastSessionIdGenerator extends StandardSessionIdGenerator {
        @Override
        protected void getRandomBytes(final byte[] bytes) {
            ThreadLocalRandom.current().nextBytes(bytes);
        }
    }

    private static class CapturingPortConnector extends Connector {
        private final IntConsumer onPort;

        private CapturingPortConnector(final IntConsumer onPort) {
            this.onPort = onPort;
        }

        @Override
        protected void startInternal() throws LifecycleException {
            super.startInternal();
            if (getProtocolHandler() instanceof AbstractProtocol<?> ap) {
                onPort.accept(ap.getLocalPort());
            }
        }
    }
}
