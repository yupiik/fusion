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
package io.yupiik.fusion.persistence.impl.datasource.tomcat;

import io.yupiik.fusion.persistence.api.SQLFunction;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

public class TomcatDataSource extends DataSource {
    private final Class<?>[] connectionProxiedTypes = {Connection.class};
    private final ThreadLocal<Connection> connectionThreadLocal = new ThreadLocal<>();

    public TomcatDataSource(final PoolProperties properties) {
        super(properties);
    }

    // for proxy when produced by CDI
    protected TomcatDataSource() {
        super(new PoolProperties());
    }

    /**
     * Binds a connection to current thread in write mode, the result will be committed if there is no error.
     * @param function the task to execute.
     * @param <T> the returned type.
     * @return the result of the function computation.
     */
    public <T> T write(final Function<Connection, T> function) {
        return withConnection(connection -> {
            final var result = function.apply(connection);
            connection.commit();
            return result;
        });
    }

    /**
     * Binds a connection to current thread in read-only mode, the result will be rolle-backed if needed.
     * @param function the task to execute.
     * @param <T> the returned type.
     * @return the result of the function computation.
     */
    public <T> T read(final Function<Connection, T> function) {
        return withConnection(connection -> {
            final var readOnly = connection.isReadOnly();
            connection.setReadOnly(true);
            try {
                return function.apply(connection);
            } finally {
                if (!readOnly) {
                    connection.setReadOnly(false);
                }
                if (!connection.isClosed()) {
                    connection.rollback();
                }
            }
        });
    }

    @Override
    public Connection getConnection() {
        final var existing = current();
        if (existing == null) {
            throw new IllegalStateException("No contextual connection, ensure to use DataSourceTx around your code");
        }
        return existing;
    }

    public Connection current() {
        final var connection = connectionThreadLocal.get();
        if (connection == null) {
            connectionThreadLocal.remove();
        }
        return connection;
    }

    public <T> T withConnection(final SQLFunction<Connection, T> function) {
        try (final var connection = super.getConnection()) {
            connectionThreadLocal.set(wrap(connection));
            final var original = disableAutoCommit(connection);
            try {
                return function.apply(connection);
            } catch (final RuntimeException | Error re) {
                if (!connection.isClosed()) {
                    connection.rollback();
                }
                throw re;
            } finally {
                restoreAutoCommit(connection, original);
            }
        } catch (final SQLException ex) {
            throw new IllegalStateException(ex);
        } finally {
            connectionThreadLocal.remove();
        }
    }

    private Connection wrap(final Connection connection) {
        return Connection.class.cast(Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                connectionProxiedTypes,
                new ConnectionHandler(connection)));
    }

    private boolean disableAutoCommit(final Connection connection) throws SQLException {
        final var original = connection.getAutoCommit();
        if (original) {
            connection.setAutoCommit(false);
        }
        return original;
    }

    private void restoreAutoCommit(final Connection connection, final boolean original) throws SQLException {
        if (original) {
            connection.setAutoCommit(true);
        }
    }

    private record ConnectionHandler(Connection connection) implements InvocationHandler {

        @Override
            public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                if ("close".equals(method.getName())) {
                    return null;
                }
                try {
                    final var result = method.invoke(connection, args);
                    if ("equals".equals(method.getName())) {
                        return (Boolean) result ||
                                (args[0] != null &&
                                        Proxy.isProxyClass(args[0].getClass()) &&
                                        areEquals(Proxy.getInvocationHandler(args[0]), this));
                    }
                    if ("hashCode".equals(method.getName())) {
                        return connection.hashCode();
                    }
                    return result;
                } catch (final InvocationTargetException ite) {
                    throw ite.getTargetException();
                }
            }

            private boolean areEquals(final InvocationHandler invocationHandler, final ConnectionHandler connectionHandler) {
                return invocationHandler instanceof ConnectionHandler &&
                        ConnectionHandler.class.cast(connectionHandler).connection.equals(connectionHandler.connection);
            }
        }
}
