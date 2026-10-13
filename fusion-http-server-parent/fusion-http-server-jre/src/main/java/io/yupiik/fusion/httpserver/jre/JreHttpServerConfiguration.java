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
package io.yupiik.fusion.httpserver.jre;

import java.util.concurrent.ExecutorService;

/**
 * Plain configuration of the JRE HTTP server adapter. Kept as a simple record
 * (no fusion annotation processor): the hosting module (the {@code web}
 * command) builds this instance from its own {@code yca.http} root
 * configuration.
 *
 * @param host         the bind host, defaults to {@code 127.0.0.1}.
 * @param port         the bind port, defaults to {@code 6060}; {@code 0} asks
 *                     the OS for a free ephemeral port.
 * @param virtualThreads when {@code true} (the default) the server executes
 *                     requests on a dedicated {@code newVirtualThreadPerTaskExecutor};
 *                     when {@code false} a platform cached thread pool is used
 *                     instead. Either way dispatch and body writing never block
 *                     the {@code HttpServer}'s own read loop.
 */
public record JreHttpServerConfiguration(String host, int port, boolean virtualThreads, ExecutorService executor) {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 6060;

    public JreHttpServerConfiguration {
        host = host == null || host.isBlank() ? DEFAULT_HOST : host;
        // 0 means "let the OS pick an ephemeral port"; only an explicitly negative/unset value
        // falls back to the default port.
        if (port < 0) {
            port = DEFAULT_PORT;
        }
    }

    public JreHttpServerConfiguration(final String host, final int port, final boolean virtualThreads) {
        this(host, port, virtualThreads, null);
    }
}
