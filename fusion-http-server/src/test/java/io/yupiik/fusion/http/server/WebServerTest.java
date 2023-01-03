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
package io.yupiik.fusion.http.server;

import jakarta.servlet.http.HttpServlet;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebServerTest {
    @Test
    void runEmpty() {
        try (final var server = WebServer.of(WebServer.Configuration.of().port(0))) {
            assertNotEquals(0, server.configuration().port());
        }
    }

    @Test
    void stopOnFailure() {
        final var configuration = WebServer.Configuration.of().port(0);
        configuration.unwrap(TomcatWebServerConfiguration.class).setInitializers(List.of((set, servletContext) -> {
            final var servlet = servletContext.addServlet("test", new HttpServlet() {
                @Override
                public void init() {
                    throw new IllegalArgumentException("intended error for tests");
                }
            });
            servlet.setLoadOnStartup(1);
            servlet.addMapping("/test");
        }));
        final var threadsBefore = listThreads();
        assertThrows(IllegalStateException.class, () -> WebServer.of(configuration));

        final var maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) { // support a small retry for the CI
            try {
                assertEquals(Set.of(threadsBefore), Set.of(listThreads()));
                break;
            } catch (final AssertionError ae) {
                if (i + 1 == maxRetries) {
                    throw ae;
                }
                try {
                    sleep(100);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private Thread[] listThreads() {
        final var threads = new Thread[Thread.activeCount()];
        Thread.enumerate(threads);
        return threads;
    }
}
