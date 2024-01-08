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
package io.yupiik.fusion.persistence.impl;

import io.yupiik.fusion.persistence.api.ContextLessDatabase;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.api.ResultSetWrapper;
import io.yupiik.fusion.persistence.api.StatementBinder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * When having multiple databases in its code it enables to easily extend the type to add a custom marker (discriminator).
 */
public class DelegatingContextLessDatabase implements ContextLessDatabase, AutoCloseable {
    protected final ContextLessDatabase database;

    public DelegatingContextLessDatabase(final ContextLessDatabase database) {
        this.database = database;
    }

    @Override
    public <T> T insert(final Connection connection, final T instance) {
        return database.insert(connection, instance);
    }

    @Override
    public <T> T update(final Connection connection, final T instance) {
        return database.update(connection, instance);
    }

    @Override
    public <T> T delete(final Connection connection, final T instance) {
        return database.delete(connection, instance);
    }

    @Override
    public <T, ID> T findById(final Connection connection, final Class<T> type, final ID id) {
        return database.findById(connection, type, id);
    }

    @Override
    public <T> long countAll(final Connection connection, final Class<T> type, final String whereClause, final Consumer<StatementBinder> binder) {
        return database.countAll(connection, type, whereClause, binder);
    }

    @Override
    public <T> long countAll(final Connection connection, final Class<T> type) {
        return database.countAll(connection, type);
    }

    @Override
    public <T> List<T> findAll(final Connection connection, final Class<T> type, final String whereClause, final Consumer<StatementBinder> binder) {
        return database.findAll(connection, type, whereClause, binder);
    }

    @Override
    public <T> List<T> findAll(final Connection connection, final Class<T> type) {
        return database.findAll(connection, type);
    }

    @Override
    public <T> List<T> query(final Connection connection, final Class<T> type, final String sql, final Consumer<StatementBinder> binder) {
        return database.query(connection, type, sql, binder);
    }

    @Override
    public <T> List<T> query(final Connection connection, final Class<T> type, final String sql) {
        return database.query(connection, type, sql);
    }

    @Override
    public <T> T query(final Connection connection, final String sql, final Consumer<StatementBinder> binder, final Function<ResultSetWrapper, T> resultSetMapper) {
        return database.query(connection, sql, binder, resultSetMapper);
    }

    @Override
    public <T> T query(final Connection connection, final String sql, final Function<ResultSetWrapper, T> resultSetMapper) {
        return database.query(connection, sql, resultSetMapper);
    }

    @Override
    public <T> Optional<T> querySingle(final Connection connection, final Class<T> type, final String sql, final Consumer<StatementBinder> binder) {
        return database.querySingle(connection, type, sql, binder);
    }

    @Override
    public int execute(final Connection connection, final String sql, final Consumer<StatementBinder> binder) {
        return database.execute(connection, sql, binder);
    }

    @Override
    public int[] batch(final Connection connection, final String sql, final Iterator<Consumer<StatementBinder>> binders) {
        return database.batch(connection, sql, binders);
    }

    @Override
    public <T> int[] batchInsert(final Connection connection, final Class<T> type, final Iterator<T> instances) {
        return database.batchInsert(connection, type, instances);
    }

    @Override
    public <T> int[] batchUpdate(final Connection connection, final Class<T> type, final Iterator<T> instances) {
        return database.batchUpdate(connection, type, instances);
    }

    @Override
    public <T> int[] batchDelete(final Connection connection, final Class<T> type, final Iterator<T> instances) {
        return database.batchDelete(connection, type, instances);
    }

    @Override
    public <T> T mapOne(final Class<T> type, final ResultSet resultSet) {
        return database.mapOne(type, resultSet);
    }

    @Override
    public <T> List<T> mapAll(final Class<T> type, final ResultSet resultSet) {
        return database.mapAll(type, resultSet);
    }

    @Override
    public <T, ID> Entity<T, ID> entity(final Class<T> type) {
        return database.entity(type);
    }

    @Override
    public void close() throws Exception {
        if (database instanceof AutoCloseable c) {
            c.close();
        }
    }
}
