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

/**
 * Represents a server, started when instantiated.
 */
public interface WebServer extends AutoCloseable, Unwrappable {
    /**
     * Stops and clean the server resources.
     */
    @Override
    void close();

    /**
     * @return server configuration.
     */
    Configuration configuration();

    /**
     * Blocks until the server is shutdown or killed.
     */
    void await();

    /**
     * Creates and starts a server.
     *
     * @param configuration the webservice configuration.
     * @return the started server.
     */
    static WebServer of(final Configuration configuration) {
        return WebServerProvider.provider().create(configuration);
    }

    /**
     * Portable configuration, for more advanced setup check the dedicated server implementation
     * (e.g. the Tomcat one in {@code fusion-http-server-tomcat}).
     */
    interface Configuration extends Unwrappable {
        /**
         * @param mapping if fusion servlet (custom endpoints) should be supported its mapping (servlet pattern), {@code -} to disable. Default to {@code /}.
         * @return this.
         */
        Configuration fusionServletMapping(String mapping);

        /**
         * @param enabled if requests/responses should be considered encoded in UTF-8 (default).
         * @return this.
         */
        Configuration utf8Setup(boolean enabled);

        /**
         * @param webappBaseDir a root webapp folder (optional).
         * @return this.
         */
        Configuration base(String webappBaseDir);

        /**
         * @param port server port, can be 0 to be random.
         * @return this.
         */
        Configuration port(int port);

        /**
         * @param host server host, default to localhost.
         * @return this.
         */
        Configuration host(String host);

        /**
         * @param accessLogPattern access log pattern, default to common access log pattern.
         * @return this.
         */
        Configuration accessLogPattern(String accessLogPattern);

        /**
         * @return the runtime host.
         */
        String host();

        /**
         * @return the runtime port, enables to get the active port once the server started and the startup value was 0.
         */
        int port();

        /**
         * @return a new configuration instance.
         */
        static Configuration of() {
            return WebServerProvider.provider().configuration();
        }
    }
}
