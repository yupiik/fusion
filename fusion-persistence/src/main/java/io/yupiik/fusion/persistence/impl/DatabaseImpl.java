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
import io.yupiik.fusion.persistence.impl.operation.Operations;
import io.yupiik.fusion.persistence.impl.query.CompiledQuery;
import io.yupiik.fusion.persistence.impl.query.QueryCompiler;
import io.yupiik.fusion.persistence.impl.query.QueryKey;
import io.yupiik.fusion.persistence.impl.query.StatementBinderImpl;
import io.yupiik.fusion.persistence.impl.translation.DefaultTranslation;
import io.yupiik.fusion.persistence.impl.translation.H2Translation;
import io.yupiik.fusion.persistence.impl.translation.MySQLTranslation;
import io.yupiik.fusion.persistence.impl.translation.OracleTranslation;
import io.yupiik.fusion.persistence.impl.translation.PostgresTranslation;
import io.yupiik.fusion.persistence.spi.DatabaseTranslation;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
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
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class DatabaseImpl implements Database {
    private final DataSource datasource;
    private final DatabaseTranslation translation;
    private final Map<Class<?>, BaseEntity<?>> entities = new ConcurrentHashMap<>();
    private final QueryCompiler queryCompiler = new QueryCompiler(this);
    private final Function<Class<?>, Object> instanceLookup;

    public DatabaseImpl(final DatabaseConfiguration configuration) {
        this.datasource = configuration.getDataSource();
        this.instanceLookup = configuration.getInstanceLookup();
        this.translation = configuration.getTranslation() == null ? guessTranslation() : configuration.getTranslation();
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
    public Map<Class<?>, BaseEntity<?>> getEntities() {
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
                        line -> (T) mapAsMap(List.of(columns), line) :
                        getEntityImpl(type).nextProvider(columns, rset);
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
                        (T) mapAsMap(List.of(columns), rset) :
                        getEntityImpl(type).nextProvider(columns, rset).apply(rset);
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
    public <M> M operation(final Class<M> api) {
        final var loader = ofNullable(Thread.currentThread().getContextClassLoader())
                .or(() -> ofNullable(api.getClassLoader()))
                .orElseGet(ClassLoader::getSystemClassLoader);
        return api.cast(Proxy.newProxyInstance(
                loader,
                new Class<?>[]{api},
                new Operations(this, api, loader)));
    }

    @Override
    public <T> T insert(final T instance) {
        requireNonNull(instance, "can't persist a null instance");
        final var model = (BaseEntity<T>) getEntityImpl(instance.getClass());
        final var insertQuery = model.getInsertQuery();
        try (final var connection = datasource.getConnection();
             final var stmt = !model.isAutoIncremented() ?
                     connection.prepareStatement(insertQuery) :
                     connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS)) {
            model.onInsert(instance, stmt);
            if (stmt.executeUpdate() == 0) {
                throw new PersistenceException("Can't save " + instance);
            }
            return model.onAfterInsert(instance, stmt);
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> T update(final T instance) {
        requireNonNull(instance, "can't update a null instance");
        final var model = getEntityImpl(instance.getClass());
        try (final var connection = datasource.getConnection();
             final var stmt = connection.prepareStatement(model.getUpdateQuery())) {
            model.onUpdate(instance, stmt);
            if (stmt.executeUpdate() == 0) {
                throw new PersistenceException("Can't update " + instance);
            }
            return instance;
        } catch (final SQLException ex) {
            throw new PersistenceException(ex);
        }
    }

    @Override
    public <T> T delete(final T instance) {
        requireNonNull(instance, "can't delete a null instance");
        final var model = getEntityImpl(instance.getClass());
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
    public <T> T findById(final Class<T> type, final Object id) {
        requireNonNull(type, "can't find an instance without a type");
        final var model = getEntityImpl(type);
        try (final var connection = datasource.getConnection();
             final var stmt = connection.prepareStatement(model.getFindByIdQuery())) {
            model.onFindById(stmt, id);
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
    public <T> T mapOne(final Class<T> type, final ResultSet resultSet) {
        return type == Map.class ?
                (T) mapAsMap(toNames(resultSet), resultSet) :
                getEntityImpl(type).nextProvider(resultSet).apply(resultSet);
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
                getEntityImpl(type).nextProvider(resultSet);
        return new ResultSetWrapperImpl(resultSet).mapAll(provider::apply);
    }

    @Override
    public <T> Entity<T> getOrCreateEntity(final Class<T> type) {
        return getEntityImpl(type);
    }

    private <T> String[] getAndCacheColumns(final CompiledQuery<T> compiledQuery, final ResultSet rset) {
        final String[] columns;
        if (compiledQuery.getColumnNames() != null) {
            columns = compiledQuery.getColumnNames();
        } else {
            columns = toNames(rset).toArray(String[]::new);
            compiledQuery.setColumnNames(columns);
        }
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
            names = IntStream.rangeClosed(1, metaData.getColumnCount()).mapToObj(i -> {
                try {
                    final var columnLabel = metaData.getColumnLabel(i);
                    return columnLabel == null || columnLabel.isBlank() ? metaData.getColumnName(i) : columnLabel;
                } catch (final SQLException e) {
                    throw new IllegalStateException(e);
                }
            }).collect(toList());
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

    private <T> BaseEntity<T> getEntityImpl(final Class<T> type) {
        return (BaseEntity<T>) entities.computeIfAbsent(type, t -> new BaseEntity<>(this, t, translation));
    }

    private DatabaseTranslation guessTranslation() {
        try (final var connection = datasource.getConnection()) {
            final var url = connection.getMetaData().getURL().toLowerCase(ROOT);
            if (url.contains("oracle")) {
                return new OracleTranslation();
            }
            if (url.contains("mariadb") || url.contains("mysql")) {
                return new MySQLTranslation();
            }
            if (url.contains("postgres")) {
                return new PostgresTranslation();
            }
            if (url.contains("jdbc:h2:") || url.contains("h2 database")) {
                return new H2Translation();
            }
            if (url.contains("cloudscape") || url.contains("idb") || url.contains("daffodil")) {
                return new DefaultTranslation();
            }
            /*
            if (url.contains("sqlserver")) {
                return dbdictionaryPlugin.unalias("sqlserver");
            }
            if (url.contains("jsqlconnect")) {
                return dbdictionaryPlugin.unalias("sqlserver");
            }
            if (url.contains("sybase")) {
                return dbdictionaryPlugin.unalias("sybase");
            }
            if (url.contains("adaptive server")) {
                return dbdictionaryPlugin.unalias("sybase");
            }
            if (url.contains("informix") || url.contains("ids")) {
                return dbdictionaryPlugin.unalias("informix");
            }
            if (url.contains("ingres")) {
                return dbdictionaryPlugin.unalias("ingres");
            }
            if (url.contains("hsql")) {
                return dbdictionaryPlugin.unalias("hsql");
            }
            if (url.contains("foxpro")) {
                return dbdictionaryPlugin.unalias("foxpro");
            }
            if (url.contains("interbase")) {
                return InterbaseDictionary.class.getName();
            }
            if (url.contains("jdatastore")) {
                return JDataStoreDictionary.class.getName();
            }
            if (url.contains("borland")) {
                return JDataStoreDictionary.class.getName();
            }
            if (url.contains("access")) {
                return dbdictionaryPlugin.unalias("access");
            }
            if (url.contains("pointbase")) {
                return dbdictionaryPlugin.unalias("pointbase");
            }
            if (url.contains("empress")) {
                return dbdictionaryPlugin.unalias("empress");
            }
            if (url.contains("firebird")) {
                return FirebirdDictionary.class.getName();
            }
            if (url.contains("cache")) {
                return CacheDictionary.class.getName();
            }
            if (url.contains("derby")) {
                return dbdictionaryPlugin.unalias("derby");
            }
            if (url.contains("sapdb")) {
                return dbdictionaryPlugin.unalias("maxdb");
            }
            if (url.contains("herddb")) {
                return dbdictionaryPlugin.unalias("herddb");
            }
            if (url.contains("db2") || url.contains("as400")) {
                return dbdictionaryPlugin.unalias("db2");
            }
            if (url.contains("soliddb")) {
                return dbdictionaryPlugin.unalias("soliddb");
            }
            */
            throw new IllegalArgumentException("" +
                    "Unknown database: '" + url + "'. " +
                    "Can't find a database translation to use, set it in the configuration. " +
                    "If you are not sure, you can start by setting `io.yupiik.fusion.persistence.impl.translation.DefaultTranslation`");
        } catch (final SQLException ex) {
            throw new IllegalArgumentException("Cant find database translation, probably set it in the configuration", ex);
        }
    }
}
