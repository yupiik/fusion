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
package io.yupiik.fusion.persistence.impl.query;

import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.api.StatementBinder;
import io.yupiik.fusion.persistence.impl.DatabaseImpl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class StatementBinderImpl implements StatementBinder, AutoCloseable {
    private final DatabaseImpl database;
    private final Connection connection;
    private final String sql;
    private PreparedStatement preparedStatement;
    private int index = 1;

    public StatementBinderImpl(final DatabaseImpl database, final String sql, final Connection connection) {
        this.database = database;
        this.connection = connection;
        this.sql = sql;
    }

    @Override
    public StatementBinderImpl withReadOnlyForwardOnlyStatement() {
        if (preparedStatement == null) {
            try {
                preparedStatement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            } catch (final SQLException e) {
                throw new PersistenceException(e);
            }
        } else {
            throw new PersistenceException("Statement is already created, ensure to call withReadOnlyForwardOnlyStatement() first on the StatementBinder");
        }
        return this;
    }

    @Override
    public StatementBinderImpl withFetchSize(final int fetchSize) {
        try {
            getPreparedStatement().setFetchSize(fetchSize);
        } catch (final SQLException e) {
            throw new PersistenceException(e);
        }
        return this;
    }

    @Override
    public StatementBinderImpl bind(final Class<?> type, final Object instance) {
        try {
            database.doBind(getPreparedStatement(), index++, instance, type);
        } catch (final SQLException e) {
            throw new PersistenceException(e);
        }
        return this;
    }

    public PreparedStatement getPreparedStatement() {
        if (preparedStatement == null) {
            try {
                preparedStatement = connection.prepareStatement(sql);
            } catch (final SQLException e) {
                throw new PersistenceException(e);
            }
        }
        return preparedStatement;
    }

    public void reset() {
        index = 1;
    }

    @Override
    public void close() throws SQLException {
        if (preparedStatement != null) {
            preparedStatement.close();
        }
    }
}
