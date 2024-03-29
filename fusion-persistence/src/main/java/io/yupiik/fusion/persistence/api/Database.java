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
package io.yupiik.fusion.persistence.api;

import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;
import io.yupiik.fusion.persistence.impl.DatabaseImpl;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.yupiik.fusion.persistence.api.StatementBinder.NONE;

/**
 * Database operation repository.
 * <p>
 * IMPORTANT: there is no transaction management there, if you need one, ensure your datasource does
 * or is set up to run in a transactional context (autoCommit setup in particular).
 */
public interface Database extends BaseDatabase {
    <T> T insert(T instance);

    <T> T update(T instance);

    <T> T delete(T instance);

    /**
     * @param type entity type.
     * @param id   the identifier of the entity or an array with all identifiers if they are multiple (sorted with {@Id#order()}).
     * @param <T>  entity type.
     * @return the entity to find or null of none is found.
     */
    <T, ID> T findById(Class<T> type, ID id);

    <T> long countAll(Class<T> type, String whereClause, Consumer<StatementBinder> binder);

    default <T> long countAll(final Class<T> type) {
        return countAll(type, "", NONE);
    }

    <T> List<T> findAll(Class<T> type, String whereClause, Consumer<StatementBinder> binder);

    default <T> List<T> findAll(final Class<T> type) {
        return findAll(type, "", NONE);
    }

    <T> List<T> query(Class<T> type, String sql, Consumer<StatementBinder> binder);

    default <T> List<T> query(Class<T> type, String sql) {
        return query(type, sql, NONE);
    }

    <T> T query(String sql,
                Consumer<StatementBinder> binder,
                Function<ResultSetWrapper, T> resultSetMapper);

    default <T> T query(String sql, Function<ResultSetWrapper, T> resultSetMapper) {
        return query(sql, NONE, resultSetMapper);
    }

    <T> Optional<T> querySingle(Class<T> type, String sql, Consumer<StatementBinder> binder);

    int execute(String sql, Consumer<StatementBinder> binder);

    int[] batch(String sql, Iterator<Consumer<StatementBinder>> binders);

    <T> int[] batchInsert(Class<T> type, Iterator<T> instances);

    <T> int[] batchUpdate(Class<T> type, Iterator<T> instances);

    <T> int[] batchDelete(Class<T> type, Iterator<T> instances);

    static Database of(final DatabaseConfiguration configuration) {
        return new DatabaseImpl(configuration);
    }
}
