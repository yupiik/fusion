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
package io.yupiik.fusion.persistence.impl;

import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.api.ResultSetWrapper;
import io.yupiik.fusion.persistence.api.StatementBinder;

import java.sql.ResultSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * When having multiple databases in its code it enables to easily extend the type to add a custom marker (discriminator).
 */
public class DelegatingDatabase implements Database, AutoCloseable {
    protected final Database database;

    public DelegatingDatabase(final Database database) {
        this.database = database;
    }

    @Override
    public <T> T insert(final T instance) {
        return database.insert(instance);
    }

    @Override
    public <T> T update(final T instance) {
        return database.update(instance);
    }

    @Override
    public <T> T delete(final T instance) {
        return database.delete(instance);
    }

    @Override
    public <T, ID> T findById(final Class<T> type, final ID id) {
        return database.findById(type, id);
    }

    @Override
    public <T> long countAll(final Class<T> type, final String whereClause, final Consumer<StatementBinder> binder) {
        return database.countAll(type, whereClause, binder);
    }

    @Override
    public <T> long countAll(final Class<T> type) {
        return database.countAll(type);
    }

    @Override
    public <T> List<T> findAll(final Class<T> type, final String whereClause, final Consumer<StatementBinder> binder) {
        return database.findAll(type, whereClause, binder);
    }

    @Override
    public <T> List<T> findAll(final Class<T> type) {
        return database.findAll(type);
    }

    @Override
    public <T> List<T> query(final Class<T> type, final String sql, final Consumer<StatementBinder> binder) {
        return database.query(type, sql, binder);
    }

    @Override
    public <T> List<T> query(final Class<T> type, final String sql) {
        return database.query(type, sql);
    }

    @Override
    public <T> T query(final String sql, final Consumer<StatementBinder> binder, final Function<ResultSetWrapper, T> resultSetMapper) {
        return database.query(sql, binder, resultSetMapper);
    }

    @Override
    public <T> T query(final String sql, final Function<ResultSetWrapper, T> resultSetMapper) {
        return database.query(sql, resultSetMapper);
    }

    @Override
    public <T> Optional<T> querySingle(final Class<T> type, final String sql, final Consumer<StatementBinder> binder) {
        return database.querySingle(type, sql, binder);
    }

    @Override
    public int execute(final String sql, final Consumer<StatementBinder> binder) {
        return database.execute(sql, binder);
    }

    @Override
    public int[] batch(final String sql, final Iterator<Consumer<StatementBinder>> binders) {
        return database.batch(sql, binders);
    }

    @Override
    public <T> int[] batchInsert(final Class<T> type, final Iterator<T> instances) {
        return database.batchInsert(type, instances);
    }

    @Override
    public <T> int[] batchUpdate(final Class<T> type, final Iterator<T> instances) {
        return database.batchUpdate(type, instances);
    }

    @Override
    public <T> int[] batchDelete(final Class<T> type, final Iterator<T> instances) {
        return database.batchDelete(type, instances);
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
    public <T, ID> Entity<T, ID> getOrCreateEntity(final Class<T> type) {
        return database.getOrCreateEntity(type);
    }

    @Override
    public void close() throws Exception {
        if (database instanceof AutoCloseable c) {
            c.close();
        }
    }
}
