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

import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.api.PersistenceException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.joining;

public abstract class BaseEntity<A, B> implements Entity<A, B> {
    private final DatabaseConfiguration configuration;
    private final String[] ddl;
    private final Class<?> rootType;
    private final String table;
    private final String findById;
    private final String updateById;
    private final String deleteById;
    private final String insert;
    private final String findAll;
    private final List<ColumnMetadata> columns;
    private final boolean autoIncremented;
    private final SQLBiConsumer<A, PreparedStatement> onInsert;
    private final SQLBiConsumer<A, PreparedStatement> onUpdate;
    private final SQLBiConsumer<A, PreparedStatement> onDelete;
    private final SQLBiConsumer<B, PreparedStatement> onFindById;
    private final SQLBiFunction<A, PreparedStatement, A> onAfterInsert;

    public BaseEntity(final DatabaseConfiguration configuration, final String[] ddl,
                      final Class<?> rootType, final String table,
                      final String findById, final String updateById, final String deleteById,
                      final String insert, final String findAll,
                      final List<ColumnMetadata> columns, final boolean autoIncremented,
                      final SQLBiConsumer<A, PreparedStatement> onInsert,
                      final SQLBiConsumer<A, PreparedStatement> onUpdate,
                      final SQLBiConsumer<A, PreparedStatement> onDelete,
                      final SQLBiConsumer<B, PreparedStatement> onFindById,
                      final SQLBiFunction<A, PreparedStatement, A> onAfterInsert) {
        this.configuration = configuration;
        this.ddl = ddl;
        this.rootType = rootType;
        this.table = table;
        this.findById = findById;
        this.updateById = updateById;
        this.deleteById = deleteById;
        this.insert = insert;
        this.findAll = findAll;
        this.columns = columns;
        this.autoIncremented = autoIncremented;
        this.onInsert = onInsert;
        this.onUpdate = onUpdate;
        this.onDelete = onDelete;
        this.onFindById = onFindById;
        this.onAfterInsert = onAfterInsert;
    }

    @Override
    public String[] ddl() {
        return ddl;
    }

    @Override
    public Class<?> getRootType() {
        return rootType;
    }

    @Override
    public String getTable() {
        return table;
    }

    @Override
    public String getFindByIdQuery() {
        return findById;
    }

    @Override
    public String getUpdateQuery() {
        return updateById;
    }

    @Override
    public String getDeleteQuery() {
        return deleteById;
    }

    @Override
    public String getInsertQuery() {
        return insert;
    }

    @Override
    public String getFindAllQuery() {
        return findAll;
    }

    @Override
    public List<ColumnMetadata> getOrderedColumns() {
        return columns;
    }

    @Override
    public boolean isAutoIncremented() {
        return autoIncremented;
    }

    @Override
    public void onInsert(final A instance, final PreparedStatement statement) throws SQLException {
        onInsert.accept(instance, statement);
    }

    @Override
    public void onDelete(final A instance, final PreparedStatement statement) throws SQLException {
        onDelete.accept(instance, statement);
    }

    @Override
    public void onUpdate(final A instance, final PreparedStatement statement) throws SQLException {
        onUpdate.accept(instance, statement);
    }

    @Override
    public void onFindById(final B instance, final PreparedStatement stmt) throws SQLException {
        onFindById.accept(instance, stmt);
    }

    @Override
    public A onAfterInsert(final A instance, final PreparedStatement statement) throws SQLException {
        return onAfterInsert.apply(instance, statement);
    }

    @Override
    public String concatenateColumns(final ColumnsConcatenationRequest request) {
        final var translation = configuration.getTranslation();
        return getOrderedColumns().stream()
                .filter(it -> !request.getIgnored().contains(it.javaName()) && !request.getIgnored().contains(it.columnName()))
                .map(e -> {
                    final var name = e.javaName();
                    final var field = request.getPrefix().endsWith(".") ?
                            request.getPrefix() + translation.wrapFieldName(e.columnName()) :
                            translation.wrapFieldName(request.getPrefix() + e.columnName());
                    final var alias = request.getAliasPrefix() != null ?
                            " as " + translation.wrapFieldName(!request.getAliasPrefix().isBlank() ?
                                    request.getAliasPrefix() + Character.toUpperCase(name.charAt(0)) + (name.length() > 1 ? name.substring(1) : "") :
                                    e.javaName()) :
                            "";
                    return field + alias;
                })
                .collect(joining(", "));
    }

    @Override
    public Function<ResultSet, A> nextProvider(final String[] columns, final ResultSet rset) {
        try {
            return nextProvider(toNames(rset).toArray(String[]::new), rset);
        } catch (final SQLException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public Function<ResultSet, A> nextProvider(final ResultSet resultSet) {
        try {
            return nextProvider(toNames(resultSet).toArray(String[]::new), resultSet);
        } catch (final SQLException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public Function<ResultSet, A> mapFromPrefix(final String prefix, final ResultSet resultSet) {
        try {
            return mapFromPrefix(prefix, toNames(resultSet).toArray(String[]::new));
        } catch (final SQLException e) {
            throw new PersistenceException(e);
        }
    }

    @Override
    public Function<ResultSet, A> mapFromPrefix(final String prefix, final String... columns) {
        if (prefix == null || prefix.isBlank()) {
            return toProvider(List.of(columns));
        }

        final var lcPrefix = prefix.toLowerCase(ROOT);
        return toProvider(Stream.of(columns)
                .map(it -> it.toLowerCase(ROOT).startsWith(lcPrefix) ? it.substring(prefix.length()) : null /* ignored but don't loose the index */)
                .toList());
    }

    protected abstract Function<ResultSet, A> toProvider(final List<String> columns);

    public Stream<String> toNames(final ResultSet resultSet) throws SQLException {
        final var metaData = resultSet.getMetaData();
        return IntStream.rangeClosed(1, metaData.getColumnCount()).mapToObj(i -> {
            try {
                final var columnLabel = metaData.getColumnLabel(i);
                return columnLabel == null || columnLabel.isBlank() ? metaData.getColumnName(i) : columnLabel;
            } catch (final SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    protected SQLFunction<ResultSet, String> stringOf(final int index) {
        if (index < 0) {
            return r -> null;
        }
        return r -> r.getString(index);
    }

    protected SQLFunction<ResultSet, Integer> intOf(final int index, final boolean nullable) {
        if (index < 0) {
            if (nullable) { // Integer
                return r -> null;
            }
            // int
            return r -> 0;
        }
        return r -> r.getInt(index);
    }

    protected interface SQLFunction<X, Y> {
        Y apply(X param) throws SQLException;
    }

    protected interface SQLBiFunction<P, E, R> {
        SQLBiFunction<?, ?, ?> IDENTITY = (a, b) -> a;

        R apply(P first, E second) throws SQLException;
    }

    protected interface SQLBiConsumer<P, E> {
        SQLBiConsumer<?, ?> NOOP = (a, b) -> {
        };

        void accept(P first, E second) throws SQLException;
    }
}
