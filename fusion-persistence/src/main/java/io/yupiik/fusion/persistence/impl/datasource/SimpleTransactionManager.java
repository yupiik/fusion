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
package io.yupiik.fusion.persistence.impl.datasource;

import io.yupiik.fusion.persistence.api.SQLFunction;
import io.yupiik.fusion.persistence.api.TransactionManager;
import io.yupiik.fusion.persistence.impl.SQLBiFunction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.BiFunction;
import java.util.function.Function;

// important: this class does not depend on DataSource, connection can be obtained from any source
public class SimpleTransactionManager implements TransactionManager {
    private final BiFunction<SQLBiFunction<Connection, SQLFunction<Connection, ?>, ?>, SQLFunction<Connection, ?>, ?> withConnection;
    private final boolean forceReadOnly;

    public SimpleTransactionManager(final BiFunction<SQLBiFunction<Connection, SQLFunction<Connection, ?>, ?>, SQLFunction<Connection, ?>, ?> withConnection,
                                    final boolean forceReadOnly) {
        this.withConnection = withConnection;
        this.forceReadOnly = forceReadOnly;
    }

    public SimpleTransactionManager(final Function<SQLFunction<Connection, ?>, ?> withConnection,
                                    final boolean forceReadOnly) {
        this((ctx, fn) -> withConnection.apply(c -> ctx.apply(c, fn)), forceReadOnly);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T write(final Function<Connection, T> function) {
        return (T) withConnection.apply(this::executeWithoutAutoCommit, connection -> {
            final var result = function.apply(connection);
            connection.commit();
            return result;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T writeSQL(final SQLFunction<Connection, T> function) {
        return (T) withConnection.apply(this::executeWithoutAutoCommit, connection -> {
            final var result = function.apply(connection);
            connection.commit();
            return result;
        });
    }

    /**
     * Binds a connection to current thread in read-only mode, the result will be rolled-backed if needed.
     *
     * @param function the task to execute.
     * @param <T>      the returned type.
     * @return the result of the function computation.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T read(final Function<Connection, T> function) {
        return (T) withConnection.apply((c, f) -> f.apply(c), connection -> {
            final boolean readOnly = onRead(connection);
            try {
                return executeWithoutAutoCommit(connection, function::apply);
            } finally {
                afterRead(connection, readOnly);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readSQL(final SQLFunction<Connection, T> function) {
        return (T) withConnection.apply((c, f) -> f.apply(c), connection -> {
            final boolean readOnly = onRead(connection);
            try {
                return executeWithoutAutoCommit(connection, function);
            } finally {
                afterRead(connection, readOnly);
            }
        });
    }

    public <T> T executeWithoutAutoCommit(final Connection connection, final SQLFunction<Connection, T> task) throws SQLException {
        final var original = disableAutoCommit(connection);
        try {
            return task.apply(connection);
        } catch (final RuntimeException | Error re) {
            if (!connection.isClosed()) {
                connection.rollback();
            }
            throw re;
        } finally {
            restoreAutoCommit(connection, original);
        }
    }

    private boolean onRead(final Connection connection) throws SQLException {
        if (!forceReadOnly) {
            return true;
        }

        final var readOnly = connection.isReadOnly();
        final var autoCommit = connection.getAutoCommit();
        if (!autoCommit) {
            connection.setAutoCommit(true);
        }
        try {
            connection.setReadOnly(true);
        } finally {
            if (!autoCommit) {
                connection.setAutoCommit(false);
            }
        }
        return readOnly;
    }

    private void afterRead(final Connection connection, final boolean readOnly) throws SQLException {
        if (!readOnly) {
            final var autoCommit = connection.getAutoCommit();
            if (!autoCommit) {
                connection.setAutoCommit(true);
            }
            try {
                connection.setReadOnly(false);
            } finally {
                if (!autoCommit) {
                    connection.setAutoCommit(false);
                }
            }
        }
        if (!connection.isClosed() && !connection.getAutoCommit()) {
            connection.rollback();
        }
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
}
