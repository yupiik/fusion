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
import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.api.ResultSetWrapper;
import io.yupiik.fusion.persistence.api.StatementBinder;
import io.yupiik.fusion.persistence.impl.query.CompiledQuery;
import io.yupiik.fusion.persistence.impl.query.QueryCompiler;
import io.yupiik.fusion.persistence.impl.query.QueryKey;
import io.yupiik.fusion.persistence.impl.query.StatementBinderImpl;
import io.yupiik.fusion.persistence.spi.DatabaseTranslation;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Locale.ROOT;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class DatabaseImpl implements Database {
    private final DataSource datasource;
    private final DatabaseTranslation translation;
    private final Map<Class<?>, Entity<?, ?>> entities = new ConcurrentHashMap<>();
    private final QueryCompiler queryCompiler = new QueryCompiler(this);
    private final Function<Class<?>, Object> instanceLookup;

    public DatabaseImpl(final DatabaseConfiguration configuration) {
        this.datasource = configuration.getDataSource();
        this.instanceLookup = configuration.getInstanceLookup();
        this.translation = requireNonNull(configuration.getTranslation(), "no translation found");
    }

    public Function<Class<?>, Object> getInstanceLookup() {
        return instanceLookup;
    }

    public DataSource getDatasource() {
        return datasource;
    }

    public DatabaseTranslation getTranslation() {
        return translation;
    }

    // mainly enables some cleanup if needed, not exposed as such in the API
    public Map<Class<?>, Entity<?, ?>> getEntities() {
        return entities;
    }

    @Override
    public <T> List<T> query(final Class<T> type, final String sql, final Consumer<StatementBinder> binder) {
        requireNonNull(type, "can't query without a projection");
        requireNonNull(sql, "can't query without a query");
        final var compiledQuery = queryCompiler.getOrCreate(new QueryKey<>(type, sql));
        try (final var connection = datasource.getConnection();
             final var query = compiledQuery.apply(connection)) {
            binder.accept(query);
            try (final var rset = query.getPreparedStatement().executeQuery()) {
                final var columns = getAndCacheColumns(compiledQuery, rset);
                final Function<ResultSet, T> provider = type == Map.class ?
                        line -> (T) mapAsMap(columns, line) :
                        getEntityImpl(type).mapper(columns);
                return new ResultSetWrapperImpl(rset).mapAll(provider::apply);
            }
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> Optional<T> querySingle(final Class<T> type, final String sql, final Consumer<StatementBinder> binder) {
        requireNonNull(type, "can't query without a projection");
        requireNonNull(sql, "can't query without a query");
        final var compiledQuery = queryCompiler.getOrCreate(new QueryKey<>(type, sql));
        try (final var connection = datasource.getConnection();
             final var query = compiledQuery.apply(connection)) {
            binder.accept(query);
            try (final var rset = query.getPreparedStatement().executeQuery()) {
                if (!rset.next()) {
                    return empty();
                }

                final var columns = getAndCacheColumns(compiledQuery, rset);
                final var value = type == Map.class ?
                        (T) mapAsMap(columns, rset) :
                        getEntityImpl(type).mapper(columns).apply(rset);
                if (rset.next()) {
                    return empty();
                }
                return Optional.of(value);
            }
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> T query(final String sql,
                       final Consumer<StatementBinder> binder,
                       final Function<ResultSetWrapper, T> resultSetMapper) {
        requireNonNull(resultSetMapper, "can't query without a resultset handler");
        requireNonNull(sql, "can't query without a query");
        try (final var connection = datasource.getConnection();
             final var query = queryCompiler.getOrCreate(new QueryKey<>(Object.class, sql)).apply(connection)) {
            binder.accept(query);
            try (final var rset = query.getPreparedStatement().executeQuery()) {
                return resultSetMapper.apply(new ResultSetWrapperImpl(rset));
            }
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public int execute(final String sql, final Consumer<StatementBinder> binder) {
        requireNonNull(binder, "can't execute without a binder");
        requireNonNull(sql, "can't execute without a query");
        try (final var connection = datasource.getConnection();
             final var query = queryCompiler.getOrCreate(new QueryKey<>(Object.class, sql)).apply(connection)) {
            binder.accept(query);
            return query.getPreparedStatement().executeUpdate();
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public int[] batch(final String sql, final Iterator<Consumer<StatementBinder>> binders) {
        requireNonNull(binders, "can't bind without binders");
        requireNonNull(sql, "can't execute bulk without a statement");
        try (final var connection = datasource.getConnection();
             final var stmt = new StatementBinderImpl(this, sql, connection)) {
            while (binders.hasNext()) {
                binders.next().accept(stmt);
                stmt.getPreparedStatement().addBatch();
                stmt.reset();
            }
            return stmt.getPreparedStatement().executeBatch();
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> int[] batchInsert(final Class<T> type, final Iterator<T> instances) {
        requireNonNull(type, "no type set");
        requireNonNull(instances, "no instances set");
        final var model = getEntityImpl(type);
        try (final var connection = datasource.getConnection();
             final var stmt = new StatementBinderImpl(this, model.getInsertQuery(), connection)) {
            final var preparedStatement = stmt.getPreparedStatement();
            while (instances.hasNext()) {
                model.onInsert(instances.next(), preparedStatement);
                preparedStatement.addBatch();
                stmt.reset();
            }
            return preparedStatement.executeBatch();
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> int[] batchUpdate(final Class<T> type, final Iterator<T> instances) {
        requireNonNull(type, "no type set");
        requireNonNull(instances, "no instances set");
        final var model = getEntityImpl(type);
        try (final var connection = datasource.getConnection();
             final var stmt = new StatementBinderImpl(this, model.getUpdateQuery(), connection)) {
            final var preparedStatement = stmt.getPreparedStatement();
            while (instances.hasNext()) {
                model.onUpdate(instances.next(), preparedStatement);
                preparedStatement.addBatch();
                stmt.reset();
            }
            return preparedStatement.executeBatch();
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> int[] batchDelete(final Class<T> type, final Iterator<T> instances) {
        requireNonNull(type, "no type set");
        requireNonNull(instances, "no instances set");
        final var model = getEntityImpl(type);
        try (final var connection = datasource.getConnection();
             final var stmt = new StatementBinderImpl(this, model.getDeleteQuery(), connection)) {
            final var preparedStatement = stmt.getPreparedStatement();
            while (instances.hasNext()) {
                model.onDelete(instances.next(), preparedStatement);
                preparedStatement.addBatch();
                stmt.reset();
            }
            return preparedStatement.executeBatch();
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> T insert(final T instance) {
        requireNonNull(instance, "can't persist a null instance");
        final var model = (Entity<T, ?>) getEntityImpl(instance.getClass());
        final var insertQuery = model.getInsertQuery();
        try (final var connection = datasource.getConnection();
             final var stmt = !model.isAutoIncremented() ?
                     connection.prepareStatement(insertQuery) :
                     connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS)) {
            final var inst = model.onInsert(instance, stmt);
            if (stmt.executeUpdate() == 0) {
                throw new PersistenceException("Can't save " + instance);
            }
            return model.onAfterInsert(inst, stmt);
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> T update(final T instance) {
        requireNonNull(instance, "can't update a null instance");
        final Class<T> aClass = (Class<T>) instance.getClass();
        final var model = getEntityImpl(aClass);
        try (final var connection = datasource.getConnection();
             final var stmt = connection.prepareStatement(model.getUpdateQuery())) {
            final var inst = model.onUpdate(instance, stmt);
            if (stmt.executeUpdate() == 0) {
                throw new PersistenceException("Can't update " + instance);
            }
            return inst;
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> T delete(final T instance) {
        requireNonNull(instance, "can't delete a null instance");
        final Class<T> aClass = (Class<T>) instance.getClass();
        final var model = getEntityImpl(aClass);
        try (final var connection = datasource.getConnection();
             final var stmt = connection.prepareStatement(model.getDeleteQuery())) {
            model.onDelete(instance, stmt);
            if (stmt.executeUpdate() == 0) {
                throw new PersistenceException("Can't delete " + instance);
            }
            return instance;
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T, ID> T findById(final Class<T> type, final ID id) {
        requireNonNull(type, "can't find an instance without a type");
        final var model = getEntityImpl(type);
        try (final var connection = datasource.getConnection();
             final var stmt = connection.prepareStatement(model.getFindByIdQuery())) {
            model.onFindById(id, stmt);
            try (final var rset = stmt.executeQuery()) {
                if (!rset.next()) {
                    return null;
                }
                final var res = mapOne(type, rset);
                if (rset.next()) {
                    throw new PersistenceException("Ambiguous entity fetched!");
                }
                return res;
            }
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> long countAll(final Class<T> type, final String whereClause, final Consumer<StatementBinder> binder) {
        requireNonNull(type, "can't find an instance without a type");
        final var model = getEntityImpl(type);
        final var compiledQuery = queryCompiler.getOrCreate(
                new QueryKey<>(type, model.getCountAllQuery() + ' ' + whereClause));
        try (final var connection = datasource.getConnection();
             final var query = compiledQuery.apply(connection)) {
            binder.accept(query);
            try (final var rset = query.getPreparedStatement().executeQuery()) {
                final var columns = getAndCacheColumns(compiledQuery, rset);
                if (!rset.next()) {
                    return 0L;
                }
                return rset.getLong(1);
            }
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> List<T> findAll(final Class<T> type, final String whereClause, final Consumer<StatementBinder> binder) {
        requireNonNull(type, "can't find an instance without a type");
        final var model = getEntityImpl(type);
        final var compiledQuery = queryCompiler.getOrCreate(
                new QueryKey<>(type, model.getFindAllQuery() + ' ' + whereClause));
        try (final var connection = datasource.getConnection();
             final var query = compiledQuery.apply(connection)) {
            binder.accept(query);
            try (final var rset = query.getPreparedStatement().executeQuery()) {
                final var columns = getAndCacheColumns(compiledQuery, rset);
                final Function<ResultSet, T> provider = type == Map.class ?
                        line -> (T) mapAsMap(columns, line) :
                        getEntityImpl(type).mapper(columns);
                return new ResultSetWrapperImpl(rset).mapAll(provider::apply);
            }
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> T mapOne(final Class<T> type, final ResultSet resultSet) {
        return type == Map.class ?
                (T) mapAsMap(toNames(resultSet), resultSet) :
                getEntityImpl(type).mapper(resultSet).apply(resultSet);
    }

    @Override
    public <T> List<T> mapAll(final Class<T> type, final ResultSet resultSet) {
        final var provider = type == Map.class ?
                new Function<ResultSet, T>() {
                    private final List<String> names;

                    {
                        names = toNames(resultSet);
                    }

                    @Override
                    public T apply(final ResultSet line) {
                        return (T) mapAsMap(names, line);
                    }
                } :
                getEntityImpl(type).mapper(resultSet);
        return new ResultSetWrapperImpl(resultSet).mapAll(provider::apply);
    }

    @Override
    public <T, ID> Entity<T, ID> getOrCreateEntity(final Class<T> type) {
        return getEntityImpl(type);
    }

    private <T> List<String> getAndCacheColumns(final CompiledQuery<T> compiledQuery, final ResultSet rset) {
        if (compiledQuery.getColumnNames() != null) {
            return compiledQuery.getColumnNames();
        }

        final var columns = toNames(rset);
        compiledQuery.setColumnNames(columns);
        return columns;
    }

    public void doBind(final PreparedStatement statement, final int idx, final Object value, final Class<?> type) throws SQLException {
        if (value == null) {
            bindNull(statement, idx, type);
        } else {
            statement.setObject(idx, value);
        }
    }

    public void bindNull(final PreparedStatement statement, final int idx, final Class<?> type) throws SQLException {
        if (String.class == type) {
            statement.setNull(idx, Types.VARCHAR);
        } else if (byte[].class == type) {
            statement.setNull(idx, Types.VARBINARY);
        } else if (Integer.class == type) {
            statement.setNull(idx, Types.INTEGER);
        } else if (Double.class == type) {
            statement.setNull(idx, Types.DOUBLE);
        } else if (Float.class == type) {
            statement.setNull(idx, Types.FLOAT);
        } else if (Long.class == type) {
            statement.setNull(idx, Types.BIGINT);
        } else if (Boolean.class == type) {
            statement.setNull(idx, Types.BOOLEAN);
        } else if (Date.class == type || LocalDate.class == type || LocalDateTime.class == type) {
            statement.setNull(idx, Types.DATE);
        } else if (OffsetDateTime.class == type || ZonedDateTime.class == type) {
            statement.setNull(idx, Types.TIMESTAMP_WITH_TIMEZONE);
        } else if (LocalTime.class == type) {
            statement.setNull(idx, Types.TIME);
        } else {
            statement.setNull(idx, Types.OTHER);
        }
    }

    public Object lookup(final ResultSet resultSet, final String column, final Class<?> type) throws SQLException {
        if (String.class == type) {
            return resultSet.getString(column);
        }
        if (byte.class == type) {
            return resultSet.getByte(column);
        }
        if (byte[].class == type) {
            return resultSet.getBytes(column);
        }
        if (Integer.class == type || int.class == type) {
            return resultSet.getInt(column);
        }
        if (Double.class == type || double.class == type) {
            return resultSet.getDouble(column);
        }
        if (Float.class == type || float.class == type) {
            return resultSet.getFloat(column);
        }
        if (Long.class == type || long.class == type) {
            return resultSet.getLong(column);
        }
        if (Boolean.class == type || boolean.class == type) {
            return resultSet.getBoolean(column);
        }
        if (Date.class == type) {
            return resultSet.getDate(column);
        }
        return resultSet.getObject(column, type);
    }

    public Object lookup(final ResultSet resultSet, final int column, final Class<?> type) throws SQLException {
        if (String.class == type) {
            return resultSet.getString(column);
        }
        if (byte.class == type) {
            return resultSet.getByte(column);
        }
        if (byte[].class == type) {
            return resultSet.getBytes(column);
        }
        if (Integer.class == type || int.class == type) {
            return resultSet.getInt(column);
        }
        if (Double.class == type || double.class == type) {
            return resultSet.getDouble(column);
        }
        if (Float.class == type || float.class == type) {
            return resultSet.getFloat(column);
        }
        if (Long.class == type || long.class == type) {
            return resultSet.getLong(column);
        }
        if (Boolean.class == type || boolean.class == type) {
            return resultSet.getBoolean(column);
        }
        if (Date.class == type) {
            return resultSet.getDate(column);
        }
        return resultSet.getObject(column, type);
    }

    private List<String> toNames(final ResultSet resultSet) {
        final List<String> names;
        try {
            final var metaData = resultSet.getMetaData();
            names = IntStream.rangeClosed(1, metaData.getColumnCount())
                    .mapToObj(i -> {
                        try {
                            final var columnLabel = metaData.getColumnLabel(i);
                            return columnLabel == null || columnLabel.isBlank() ? metaData.getColumnName(i) : columnLabel;
                        } catch (final SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    // most of databases are case insensitive and all can be made friendly so make the matching easier
                    .map(it -> it.toLowerCase(ROOT))
                    .collect(toList());
        } catch (final SQLException s) {
            throw new PersistenceException(s);
        }
        return names;
    }

    private Map<String, ?> mapAsMap(final List<String> names, final ResultSet line) {
        return names.stream().flatMap(it -> {
            try {
                final var object = line.getObject(it);
                return object == null ? Stream.empty() : Stream.of(entry(it, object));
            } catch (final SQLException e) {
                throw new PersistenceException(e);
            }
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SuppressWarnings("unchecked")
    private <T, ID> Entity<T, ID> getEntityImpl(final Class<T> type) {
        return (Entity<T, ID>) requireNonNull(
                entities.computeIfAbsent(type, t -> (Entity<?, ?>) getInstanceLookup().apply(type)),
                () -> "Missing entity '" + type.getName() + "', did you check you generated the related Entity model as a bean?");
    }
}
