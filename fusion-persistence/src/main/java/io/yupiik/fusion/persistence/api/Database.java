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
package io.yupiik.fusion.persistence.api;

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;
import io.yupiik.fusion.persistence.impl.DatabaseImpl;

import java.sql.ResultSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Database operation repository.
 * <p>
 * IMPORTANT: there is no transaction management there, if you need one, ensure your datasource does
 * or is set up to run in a transactional context (autoCommit setup in particular).
 */
public interface Database {
    /**
     * creates a querying API instance.
     *
     * @param api the operation API (@Operation}.
     * @param <M> the type of operation API.
     * @return an API instance.
     */
    <M> M operation(Class<M> api);

    <T> T insert(T instance);

    <T> T update(T instance);

    <T> T delete(T instance);

    /**
     * @param type entity type.
     * @param id   the identifier of the entity or an array with all identifiers if they are multiple (sorted with {@Id#order()}).
     * @param <T>  entity type.
     * @return the entity to find or null of none is found.
     */
    <T> T findById(Class<T> type, Object id);

    <T> List<T> findAll(Class<T> type);

    <T> List<T> query(Class<T> type, String sql, Consumer<StatementBinder> binder);

    <T> Optional<T> querySingle(Class<T> type, String sql, Consumer<StatementBinder> binder);

    <T> T query(String sql,
                Consumer<StatementBinder> binder,
                Function<ResultSetWrapper, T> resultSetMapper);

    int execute(String sql, Consumer<StatementBinder> binder);

    int[] batch(String sql, Iterator<Consumer<StatementBinder>> binders);

    <T> int[] batchInsert(Class<T> type, Iterator<T> instances);

    <T> int[] batchUpdate(Class<T> type, Iterator<T> instances);

    <T> int[] batchDelete(Class<T> type, Iterator<T> instances);

    /**
     * @param type      entity type.
     * @param resultSet resultset positionned at the row to map (next() already called).
     * @param <T>       entity type.
     * @return the mapped entity.
     */
    <T> T mapOne(Class<T> type, ResultSet resultSet);

    <T> List<T> mapAll(Class<T> type, ResultSet resultSet);

    <T> Entity<T> getOrCreateEntity(Class<T> type);

    static Database of(final DatabaseConfiguration configuration, final RuntimeContainer container) {
        return new DatabaseImpl(configuration, container);
    }


}
