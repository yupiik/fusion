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

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StaticResourcesTest {
    @Test
    void run(@TempDir final Path www) throws IOException, InterruptedException {
        Files.createDirectories(www);
        Files.writeString(www.resolve("index.html"), "<html><body><h1>Hi Fusion!</h1></body></html>");

        try (final var container = ConfiguringContainer.of()
                // can be replaced by
                // public void onWebConfig(@OnEvent @Order(Integer.MAX_VALUE) WebServer.Configuration conf) {...}
                .register(new FusionListener<WebServer.Configuration>() {
                    @Override
                    public Type eventType() {
                        return WebServer.Configuration.class;
                    }

                    @Override
                    public int priority() {
                        return Integer.MAX_VALUE; // last to not break custom configuration if any
                    }

                    @Override
                    public void onEvent(final RuntimeContainer container, final WebServer.Configuration event) {
                        event.port(0);

                        final var tomcat = event.unwrap(TomcatWebServerConfiguration.class);
                        tomcat.setContextCustomizers(concat(tomcat.getContextCustomizers(), ctx -> {
                            Tomcat.addDefaultMimeTypeMappings(ctx);
                            ctx.addWelcomeFile("index.html");
                            ctx.setDocBase(www.toString());
                            ctx.addServletContainerInitializer((ignored, sc) -> {
                                final var servlet = sc.addServlet("default", DefaultServlet.class);
                                servlet.setLoadOnStartup(1);
                                servlet.addMapping("/");
                                sc.setSessionTimeout(1);
                            }, Set.of());
                        }));
                    }

                    private <T> List<T> concat(final List<T> existing, final T newInstance) {
                        return existing == null ? List.of(newInstance) : Stream.concat(existing.stream(), Stream.of(newInstance)).toList();
                    }
                })
                .start();
             final var conf = container.lookup(WebServer.Configuration.class)) {
            final var url = URI.create("http://localhost:" + conf.instance().port());
            final var home = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder().GET().uri(url).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, home.statusCode());
            assertEquals("<html><body><h1>Hi Fusion!</h1></body></html>", home.body());
        }
    }
}
