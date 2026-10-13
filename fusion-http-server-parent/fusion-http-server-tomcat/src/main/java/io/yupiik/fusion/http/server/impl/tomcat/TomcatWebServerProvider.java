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

import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.api.WebServerProvider;
import io.yupiik.fusion.http.server.impl.servlet.ServletCookie;

/**
 * {@link WebServerProvider} for the Tomcat implementation, registered through
 * {@code META-INF/services}. It is picked up by the api module static factories
 * ({@link WebServer#of(WebServer.Configuration)}, {@link WebServer.Configuration#of()},
 * {@link Cookie#of()}) when this module is on the classpath.
 */
public class TomcatWebServerProvider implements WebServerProvider {
    @Override
    public WebServer create(final WebServer.Configuration configuration) {
        if (!(configuration instanceof TomcatWebServerConfiguration tomcatWebServerConfiguration)) {
            throw new IllegalArgumentException("" +
                    "Unsupported configuration: " + configuration + ", " +
                    "ensure to use WebServer.Configuration.of() or new TomcatWebServerConfiguration()");
        }
        return new TomcatWebServer(tomcatWebServerConfiguration);
    }

    @Override
    public WebServer.Configuration configuration() {
        return new TomcatWebServerConfiguration();
    }

    @Override
    public Cookie.Builder createCookie() {
        return new ServletCookie.BuilderImpl();
    }
}