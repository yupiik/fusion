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
package io.yupiik.fusion.http.server.impl.tomcat;

import jakarta.servlet.ServletContainerInitializer;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.spi.Endpoint;

import java.util.List;
import java.util.function.Consumer;

public class TomcatWebServerConfiguration implements WebServer.Configuration {
    private String fusionServletMapping = "/";
    private boolean skipUtf8Setup = false;
    private boolean disableRegistry = true;
    private boolean fastSessionId = true;
    private String base = "";
    private int port = 8080;
    private String defaultHost = "localhost";
    private String accessLogPattern = "common";
    private String compression = "on";
    private String skipAccessLogAttribute = "skip-access-log";
    private List<Endpoint> endpoints = List.of();
    private List<ServletContainerInitializer> initializers = List.of();
    private List<Consumer<Tomcat>> tomcatCustomizers = List.of(Tomcat::getConnector); // force init
    private List<Consumer<StandardContext>> contextCustomizers = List.of();
    private MonitoringServerConfiguration monitoringServerConfiguration;

    public MonitoringServerConfiguration getMonitoringServerConfiguration() {
        return monitoringServerConfiguration;
    }

    public TomcatWebServerConfiguration setMonitoringServerConfiguration(final MonitoringServerConfiguration monitoringServerConfiguration) {
        this.monitoringServerConfiguration = monitoringServerConfiguration;
        return this;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public TomcatWebServerConfiguration setEndpoints(final List<Endpoint> endpoints) {
        this.endpoints = endpoints;
        return this;
    }

    public String getFusionServletMapping() {
        return fusionServletMapping;
    }

    public void setFusionServletMapping(final String fusionServletMapping) {
        this.fusionServletMapping = fusionServletMapping;
    }

    public String getSkipAccessLogAttribute() {
        return skipAccessLogAttribute;
    }

    public void setSkipAccessLogAttribute(final String skipAccessLogAttribute) {
        this.skipAccessLogAttribute = skipAccessLogAttribute;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(final String compression) {
        this.compression = compression;
    }

    public boolean isFastSessionId() {
        return fastSessionId;
    }

    public void setFastSessionId(final boolean fastSessionId) {
        this.fastSessionId = fastSessionId;
    }

    public boolean isSkipUtf8Setup() {
        return skipUtf8Setup;
    }

    public void setSkipUtf8Setup(final boolean skipUtf8Setup) {
        this.skipUtf8Setup = skipUtf8Setup;
    }

    public void setTomcatCustomizers(final List<Consumer<Tomcat>> tomcatCustomizers) {
        this.tomcatCustomizers = tomcatCustomizers;
    }

    public void setContextCustomizers(final List<Consumer<StandardContext>> contextCustomizers) {
        this.contextCustomizers = contextCustomizers;
    }

    public void setDisableRegistry(final boolean disableRegistry) {
        this.disableRegistry = disableRegistry;
    }

    public void setBase(final String base) {
        this.base = base;
    }

    public void setPort(final int port) {
        this.port = port;
    }

    public void setDefaultHost(final String defaultHost) {
        this.defaultHost = defaultHost;
    }

    public void setAccessLogPattern(final String accessLogPattern) {
        this.accessLogPattern = accessLogPattern;
    }

    public void setInitializers(final List<ServletContainerInitializer> initializers) {
        this.initializers = initializers;
    }

    public boolean isDisableRegistry() {
        return disableRegistry;
    }

    public String getBase() {
        return base;
    }

    public int getPort() {
        return port;
    }

    public String getDefaultHost() {
        return defaultHost;
    }

    public String getAccessLogPattern() {
        return accessLogPattern;
    }

    public List<ServletContainerInitializer> getInitializers() {
        return initializers;
    }

    public List<Consumer<Tomcat>> getTomcatCustomizers() {
        return tomcatCustomizers;
    }

    public List<Consumer<StandardContext>> getContextCustomizers() {
        return contextCustomizers;
    }

    @Override
    public WebServer.Configuration fusionServletMapping(final String fusionServletMapping) {
        setFusionServletMapping(fusionServletMapping);
        return this;
    }

    @Override
    public WebServer.Configuration utf8Setup(final boolean enabled) {
        setSkipUtf8Setup(!enabled);
        return this;
    }

    @Override
    public WebServer.Configuration base(final String webappBaseDir) {
        setBase(webappBaseDir);
        return this;
    }

    @Override
    public WebServer.Configuration port(final int port) {
        setPort(port);
        return this;
    }

    @Override
    public WebServer.Configuration host(final String host) {
        setDefaultHost(host);
        return this;
    }

    @Override
    public WebServer.Configuration accessLogPattern(final String accessLogPattern) {
        setAccessLogPattern(accessLogPattern);
        return this;
    }

    @Override
    public String host() {
        return defaultHost;
    }

    @Override
    public int port() {
        return getPort();
    }
}
