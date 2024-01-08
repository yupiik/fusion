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
package io.yupiik.kubernetes.operator.base.test;

import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.testing.MonoFusionSupport;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(OperatorSupport.Impl.class)
@MonoFusionSupport
@Target(TYPE)
@Retention(RUNTIME)
public @interface OperatorSupport {
    class Impl implements ParameterResolver {
        private static MockController MOCK;
        private static final CountDownLatch LATCH = new CountDownLatch(1);

        static {
            try {
                final var mock = HttpServer.create(new InetSocketAddress("localhost", 0), 1024);
                mock.createContext("/", ex -> {
                    if ("GET".equals(ex.getRequestMethod())) {
                        switch (ex.getRequestURI().getPath()) {
                            case "/apis/fusion.yupiik.io/v1/namespaces/default/samples" -> {
                                final var query = ex.getRequestURI().getQuery();
                                final var watch = query != null && query.contains("watch=true");
                                final var response = (watch ?
                                        "" /* length=0 -> chunking as we want */ :
                                        """
                                                {
                                                  "items":[]
                                                }""").getBytes(StandardCharsets.UTF_8);
                                ex.sendResponseHeaders(200, response.length);
                                ex.getResponseBody().write(response);
                                if (watch) {
                                    MOCK = new MockController(ex);
                                    LATCH.countDown();
                                } else {
                                    ex.close();
                                }
                                return;
                            }
                            default -> {
                            }
                        }
                    }
                    try (ex) {
                        ex.sendResponseHeaders(404, 0);
                    }
                });
                mock.start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (MOCK != null) {
                        MOCK.close();
                    }
                    mock.stop(0);
                }));
                System.setProperty("operator.kubernetes.tls-skip", "true");
                System.setProperty("operator.kubernetes.token", "target/missing_token_so_ignore");
                System.setProperty("operator.kubernetes.master", "http://localhost:" + mock.getAddress().getPort());
            } catch (final IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }

        @Override
        public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
            return MockController.class == parameterContext.getParameter().getType();
        }

        @Override
        public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
            try {
                assertTrue(LATCH.await(1, MINUTES));
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(e);
            }
            return MOCK;
        }
    }
}
