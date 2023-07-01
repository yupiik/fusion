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

import io.yupiik.fusion.persistence.impl.ContextLessDatabaseConfiguration;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;
import io.yupiik.fusion.persistence.impl.DatabaseImpl;
import io.yupiik.fusion.persistence.impl.ThrowingDataSource;

import java.sql.Connection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.yupiik.fusion.persistence.api.StatementBinder.NONE;

/**
 * Similar to {@link Database} but with an explicit {@link java.sql.Connection} and its scope management
 * delegated to the caller.
 *
 * It enables to avoid any thread local usage (or structured concurrency usage) for the good.
 */
public interface ContextLessDatabase extends BaseDatabase {
    <T> T insert(Connection connection, T instance);

    <T> T update(Connection connection, T instance);

    <T> T delete(Connection connection, T instance);

    /**
     * @param type entity type.
     * @param id   the identifier of the entity or an array with all identifiers if they are multiple (sorted with {@Id#order()}).
     * @param <T>  entity type.
     * @return the entity to find or null of none is found.
     */
    <T, ID> T findById(Connection connection, Class<T> type, ID id);

    <T> long countAll(Connection connection, Class<T> type, String whereClause, Consumer<StatementBinder> binder);

    default <T> long countAll(final Connection connection, final Class<T> type) {
        return countAll(connection, type, "", NONE);
    }

    <T> List<T> findAll(Connection connection, Class<T> type, String whereClause, Consumer<StatementBinder> binder);

    default <T> List<T> findAll(final Connection connection, final Class<T> type) {
        return findAll(connection, type, "", NONE);
    }

    <T> List<T> query(Connection connection, Class<T> type, String sql, Consumer<StatementBinder> binder);

    default <T> List<T> query(Connection connection, Class<T> type, String sql) {
        return query(connection, type, sql, NONE);
    }

    <T> T query(Connection connection, String sql,
                Consumer<StatementBinder> binder,
                Function<ResultSetWrapper, T> resultSetMapper);

    default <T> T query(final Connection connection, final String sql, final Function<ResultSetWrapper, T> resultSetMapper) {
        return query(connection, sql, NONE, resultSetMapper);
    }

    <T> Optional<T> querySingle(Connection connection, Class<T> type, String sql, Consumer<StatementBinder> binder);

    int execute(Connection connection, String sql, Consumer<StatementBinder> binder);

    int[] batch(Connection connection, String sql, Iterator<Consumer<StatementBinder>> binders);

    <T> int[] batchInsert(Connection connection, Class<T> type, Iterator<T> instances);

    <T> int[] batchUpdate(Connection connection, Class<T> type, Iterator<T> instances);

    <T> int[] batchDelete(Connection connection, Class<T> type, Iterator<T> instances);

    static ContextLessDatabase of(final ContextLessDatabaseConfiguration<?> configuration) {
        return new DatabaseImpl(new DatabaseConfiguration()
                .setDataSource(new ThrowingDataSource())
                .setTranslation(configuration.getTranslation())
                .setInstanceLookup(configuration.getInstanceLookup()));
    }
}
