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
package io.yupiik.fusion.persistence.test;

import io.yupiik.fusion.persistence.impl.datasource.SimpleDataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class H2Extension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private final static ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(H2Extension.class);

    @Override
    public void beforeEach(final ExtensionContext context) {
        context.getStore(NAMESPACE).getOrComputeIfAbsent(Holder.class, h -> {
            final var url = "jdbc:h2:mem:" + context.getRequiredTestClass().getSimpleName() + "_" + context.getRequiredTestMethod().getName() + ";mode=PostgreSQL";
            final var username = "sa";
            final var password = "";
            try {
                return new Holder(url, username, password, DriverManager.getConnection(url, "SA", ""));
            } catch (final SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        ofNullable(context.getStore(NAMESPACE).get(Holder.class, Holder.class)).ifPresent(h -> {
            try {
                h.holder.close();
                for (final var c : h.connections) {
                    if (!c.isClosed()) {
                        c.close();
                    }
                }
                for (final var dataSource : h.dataSources) {
                    assertEquals(0, dataSource.counter.get());
                }
            } catch (final SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext, final ExtensionContext extensionContext) throws ParameterResolutionException {
        final var type = parameterContext.getParameter().getType();
        return Connection.class == type || DataSource.class == type;
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext, final ExtensionContext context) throws ParameterResolutionException {
        final var type = parameterContext.getParameter().getType();
        if (Connection.class == type) {
            return ofNullable(context.getStore(NAMESPACE).get(Holder.class, Holder.class))
                    .map(h -> {
                        try {
                            final var connection = DriverManager.getConnection(h.url, h.username, h.password);
                            synchronized (h) {
                                h.connections.add(connection);
                            }
                            return connection;
                        } catch (final SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .orElseThrow();
        }
        if (DataSource.class == type) {
            return ofNullable(context.getStore(NAMESPACE).get(Holder.class, Holder.class))
                    .map(h -> {
                        final var dataSource = new CountingDataSource(h.url, h.username, h.password);
                        h.dataSources.add(dataSource);
                        return dataSource;
                    })
                    .orElseThrow();
        }
        throw new ParameterResolutionException("Didn't find " + type);
    }

    private static class Holder {
        private final String url;
        private final String username;
        private final String password;
        private final Connection holder;
        private final List<Connection> connections = new ArrayList<>();
        private final List<CountingDataSource> dataSources = new ArrayList<>();

        private Holder(final String url, final String username, final String password, final Connection holder) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.holder = holder;
        }
    }

    private static class CountingDataSource extends SimpleDataSource {
        private final AtomicInteger counter = new AtomicInteger();

        private CountingDataSource(final String url, final String username, final String password) {
            super(url, username, password);
        }

        @Override
        public Connection getConnection() throws SQLException {
            counter.incrementAndGet();
            return proxy(super.getConnection());
        }

        @Override
        public Connection getConnection(final String username, final String password) throws SQLException {
            counter.incrementAndGet();
            return proxy(super.getConnection(username, password));
        }

        private Connection proxy(final Connection connection) {
            return Connection.class.cast(Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName()) && !connection.isClosed()) {
                            counter.decrementAndGet();
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (final InvocationTargetException ite) {
                            throw ite.getTargetException();
                        }
                    }));
        }
    }
}
