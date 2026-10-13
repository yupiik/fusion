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
package io.yupiik.fusion.http.server.api;

import java.util.ServiceLoader;

/**
 * SPI providing the concrete server and cookie implementations behind the
 * {@link WebServer} and {@link Cookie} static factories.
 *
 * <p>The {@code fusion-http-server-api} module carries only the contracts and
 * the shared (server-agnostic) helpers; a server implementation (the Tomcat
 * one in {@code fusion-http-server-tomcat}, or a user/test provider) registers
 * a {@code WebServerProvider} through the {@link ServiceLoader} mechanism. The
 * instance is resolved once and cached, and can be overridden with
 * {@link #setProvider(WebServerProvider)}.</p>
 */
public interface WebServerProvider {
    /**
     * @param configuration the server configuration.
     * @return a started server.
     */
    WebServer create(WebServer.Configuration configuration);

    /**
     * @return a blank default {@link WebServer.Configuration}, as produced by {@link WebServer.Configuration#of()}.
     * @throws IllegalStateException when the provider does not expose a default configuration.
     */
    default WebServer.Configuration configuration() {
        throw new IllegalStateException("Provider " + getClass().getName() + " does not provide a default configuration.");
    }

    /**
     * @return a {@link Cookie.Builder cookie builder} implementation.
     */
    Cookie.Builder createCookie();

    /**
     * @return the current provider, resolved lazily through {@link ServiceLoader} and cached.
     * @throws IllegalStateException if no provider is available (neither registered on the classpath
     *                               nor set with {@link #setProvider(WebServerProvider)}).
     */
    static WebServerProvider provider() {
        final var instance = Holder.INSTANCE;
        if (instance == null) {
            throw new IllegalStateException(
                    "No WebServerProvider available: add a server implementation dependency " +
                            "(e.g. fusion-http-server-tomcat) to the classpath or call WebServerProvider.setProvider(...).");
        }
        return instance;
    }

    /**
     * Overrides the provider used by {@link #provider()}, primarily for tests or custom wiring.
     *
     * @param provider the provider to use; {@code null} restores the {@link ServiceLoader} resolution.
     */
    static void setProvider(final WebServerProvider provider) {
        if (provider == null) {
            Holder.INSTANCE = null;
            return;
        }
        Holder.INSTANCE = provider;
    }

    final class Holder {
        private static volatile WebServerProvider INSTANCE = load();

        private Holder() {
            // no-op
        }

        private static WebServerProvider load() {
            return ServiceLoader.load(WebServerProvider.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .findFirst()
                    .orElse(null);
        }
    }
}