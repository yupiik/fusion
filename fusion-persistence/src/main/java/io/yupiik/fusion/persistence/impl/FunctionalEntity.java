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

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.api.SQLFunction;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.function.Function;

/**
 * Concrete entity backed by typed, checked direct callbacks.
 * Configuration, lifecycle dependencies and projections belong to the entity/container,
 * never to a static generated holder.
 *
 * @param <A> entity type
 * @param <B> identifier type
 */
public final class FunctionalEntity<A, B> extends BaseEntity<A, B> {

    /**
     * Uses BaseEntity SQL construction, including generated keys and RETURNING.
     * Columns must be in SQL binding order, including identifier order.
     * Null callbacks denote only logical no-ops (identity mutations or no bindings).
     * Projection preparation and row construction are always required.
     */
    public <P> FunctionalEntity(final Context context,
                                final Class<?> rootType, final String table,
                                final List<ColumnMetadata> columns, final boolean autoIncremented,
                                final Callback<A, PreparedStatement, A> insert,
                                final Callback<A, PreparedStatement, A> update,
                                final Callback<A, PreparedStatement, ?> delete,
                                final Callback<B, PreparedStatement, ?> findById,
                                final Callback<A, ResultSet, A> afterInsert,
                                final Callback<List<String>, Void, P> projection,
                                final Callback<P, ResultSet, A> row) {
        super(context.configuration(), rootType, table, List.copyOf(columns), autoIncremented,
                mutation(context, insert),
                mutation(context, update),
                binding(context, delete),
                binding(context, findById),
                mutation(context, afterInsert),
                mapper(context, projection, row));
    }

    /** Per-entity context; generated static helpers must not retain it. */
    public record Context(DatabaseConfiguration configuration, RuntimeContainer container) {
    }

    /** Direct checked SQL boundary; no unchecked exception carrier or operation ID is needed. */
    @FunctionalInterface
    public interface Callback<T, U, R> {
        R apply(Context context, T first, U second) throws SQLException;
    }

    private static <T, U> SQLBiFunction<T, U, T> mutation(final Context context, final Callback<T, U, T> callback) {
        return callback == null ? FunctionalEntity::identity : (value, statement) -> callback.apply(context, value, statement);
    }

    private static <T, U> T identity(final T value, final U ignored) {
        return value;
    }

    private static <T, U> SQLBiConsumer<T, U> binding(final Context context, final Callback<T, U, ?> callback) {
        return callback == null ? FunctionalEntity::noBinding : (value, statement) -> callback.apply(context, value, statement);
    }

    private static <T, U> void noBinding(final T value, final U ignored) {
        // Shared logical no-op, never emitted per entity.
    }

    private static <A, P> SQLFunction<List<String>, Function<ResultSet, A>> mapper(
            final Context context, final Callback<List<String>, Void, P> projection,
            final Callback<P, ResultSet, A> row) {
        return columns -> new RowMapper<>(context, row, projection.apply(context, columns, null));
    }

    // Nullable JDBC plumbing is shared; Java argument evaluation reads each generated accessor once.
    public static void bindString(final PreparedStatement statement, final int index, final String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    public static void bindEnum(final PreparedStatement statement, final int index, final Enum<?> value) throws SQLException {
        bindString(statement, index, value == null ? null : value.name());
    }

    public static void bindInt(final PreparedStatement statement, final int index, final Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    public static void bindLong(final PreparedStatement statement, final int index, final Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    public static void bindDouble(final PreparedStatement statement, final int index, final Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }

    public static void bindFloat(final PreparedStatement statement, final int index, final Float value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.FLOAT);
        } else {
            statement.setFloat(index, value);
        }
    }

    public static void bindBoolean(final PreparedStatement statement, final int index, final Boolean value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BOOLEAN);
        } else {
            statement.setBoolean(index, value);
        }
    }

    public static void bindByte(final PreparedStatement statement, final int index, final Byte value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.SMALLINT);
        } else {
            statement.setByte(index, value);
        }
    }

    public static void bindShort(final PreparedStatement statement, final int index, final Short value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.SMALLINT);
        } else {
            statement.setShort(index, value);
        }
    }

    public static void bindDate(final PreparedStatement statement, final int index, final Date value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, value);
        }
    }

    public static void bindBigDecimal(final PreparedStatement statement, final int index, final BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    public static void bindBytes(final PreparedStatement statement, final int index, final byte[] value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARBINARY);
        } else {
            statement.setBytes(index, value);
        }
    }

    // Public readers for generated direct callbacks, preserving the legacy generated reader semantics.
    // Non-native types use configuration.getTranslation().reader(index, type), not objectOf.
    public static SQLFunction<ResultSet, String> stringOf(final int index) {
        return BaseEntity.stringOf(index);
    }

    public static <T extends Enum<T>> SQLFunction<ResultSet, T> enumOf(final int index, final Class<T> type) {
        return BaseEntity.enumOf(index, type);
    }

    public static SQLFunction<ResultSet, Long> longOf(final int index, final boolean nullable) {
        return BaseEntity.longOf(index, nullable);
    }

    public static SQLFunction<ResultSet, Integer> intOf(final int index, final boolean nullable) {
        return BaseEntity.intOf(index, nullable);
    }

    public static SQLFunction<ResultSet, Double> doubleOf(final int index, final boolean nullable) {
        return BaseEntity.doubleOf(index, nullable);
    }

    public static SQLFunction<ResultSet, Float> floatOf(final int index, final boolean nullable) {
        return BaseEntity.floatOf(index, nullable);
    }

    public static SQLFunction<ResultSet, Boolean> booleanOf(final int index, final boolean nullable) {
        return BaseEntity.booleanOf(index, nullable);
    }

    public static SQLFunction<ResultSet, Short> shortOf(final int index, final boolean nullable) {
        return BaseEntity.shortOf(index, nullable);
    }

    public static SQLFunction<ResultSet, Byte> byteOf(final int index, final boolean nullable) {
        return BaseEntity.byteOf(index, nullable);
    }

    public static SQLFunction<ResultSet, Date> dateOf(final int index) {
        return BaseEntity.dateOf(index);
    }

    public static SQLFunction<ResultSet, BigDecimal> bigdecimalOf(final int index) {
        return BaseEntity.bigdecimalOf(index);
    }

    public static SQLFunction<ResultSet, byte[]> bytesOf(final int index) {
        return BaseEntity.bytesOf(index);
    }

    private record RowMapper<A, P>(Context context, Callback<P, ResultSet, A> callback, P projection)
            implements Function<ResultSet, A> {
        @Override
        public A apply(final ResultSet row) {
            try {
                return callback.apply(context, projection, row);
            } catch (final SQLException e) {
                throw new PersistenceException(e);
            }
        }
    }
}
