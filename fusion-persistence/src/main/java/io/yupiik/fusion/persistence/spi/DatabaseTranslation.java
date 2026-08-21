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
package io.yupiik.fusion.persistence.spi;

import io.yupiik.fusion.persistence.api.SQLFunction;

import java.math.BigDecimal;
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
import java.util.List;

import static java.util.stream.Collectors.joining;

public interface DatabaseTranslation {
    /**
     * Wraps a field name so the storage can quote or case-map it according to the dialect.
     * Standalone (non qualified) column names go through this method, for example when binding
     * or reading a column of an entity.
     *
     * @param name the raw column name of the entity model.
     * @return the wrapped column name usable in a SQL statement.
     */
    default String wrapFieldName(final String name) {
        return name;
    }

    /**
     * Wraps a table name so the storage can quote or case-map it according to the dialect.
     *
     * @param table the raw table name of the entity model.
     * @return the wrapped table name usable in a SQL statement.
     */
    default String wrapTableName(final String table) {
        return table;
    }

    /**
     * @return {@code true} if the JDBC driver implements {@code getGeneratedKeys()} for inserts,
     * {@code false} if generated keys must be fetched through a RETURNING clause
     * (see {@link #withReturningColumns(String, List)}).
     */
    default boolean supportsGeneratedKeys() {
        return true;
    }

    /**
     * Returns the INSERT statement completed with the clause reading back the generated keys when
     * {@link #supportsGeneratedKeys()} is {@code false}.
     *
     * @param insertQuery the INSERT statement to complete.
     * @param idColumns   auto incremented column names of the entity.
     * @return the executable INSERT statement, e.g. {@code "insert into t (a) values (?) RETURNING id"}.
     */
    default String withReturningColumns(final String insertQuery, final List<String> idColumns) {
        return insertQuery + " RETURNING " + idColumns.stream().map(this::wrapFieldName).collect(joining(", "));
    }

    /**
     * Binds an object parameter. Default implementation binds nulls through {@link #bindNull(PreparedStatement, int, Class)}
     * and delegates other values to {@code setObject(value)}.
     *
     * @param statement    statement to bind.
     * @param index        index of the parameter to bind.
     * @param value        value to bind, can be null.
     * @param declaredType declared type of the parameter, can be null when unknown.
     */
    default void bind(final PreparedStatement statement, final int index, final Object value,
                      final Class<?> declaredType) throws SQLException {
        if (value == null) {
            bindNull(statement, index, declaredType);
        } else {
            statement.setObject(index, value);
        }
    }

    /**
     * Binds a null marker for the given type. Default implementation uses standard SQL types.
     */
    default void bindNull(final PreparedStatement statement, final int index, final Class<?> type) throws SQLException {
        if (String.class == type) {
            statement.setNull(index, Types.VARCHAR);
        } else if (byte[].class == type) {
            statement.setNull(index, Types.VARBINARY);
        } else if (Integer.class == type) {
            statement.setNull(index, Types.INTEGER);
        } else if (Double.class == type) {
            statement.setNull(index, Types.DOUBLE);
        } else if (Float.class == type) {
            statement.setNull(index, Types.FLOAT);
        } else if (Long.class == type) {
            statement.setNull(index, Types.BIGINT);
        } else if (Boolean.class == type) {
            statement.setNull(index, Types.BOOLEAN);
        } else if (Date.class == type || LocalDate.class == type || LocalDateTime.class == type) {
            statement.setNull(index, Types.DATE);
        } else if (OffsetDateTime.class == type || ZonedDateTime.class == type) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else if (LocalTime.class == type) {
            statement.setNull(index, Types.TIME);
        } else if (BigDecimal.class == type) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setNull(index, Types.OTHER);
        }
    }

    /**
     * Reads a column as the given type. Default implementation delegates to {@code getObject(column, type)}:
     * it is only used for types without a native {@code getXxx} accessor.
     *
     * @param resultSet result set positioned on the row to read.
     * @param index     jdbc index of the column to read.
     * @param type      java type to map the column to.
     */
    default Object getObject(final ResultSet resultSet, final int index, final Class<?> type) throws SQLException {
        return resultSet.getObject(index, type);
    }

    /**
     * Same as {@link #getObject(ResultSet, String, Class)} but from a column label/name.
     */
    default Object getObject(final ResultSet resultSet, final String column, final Class<?> type) throws SQLException {
        return resultSet.getObject(column, type);
    }

    /**
     * Creates a lazy reader for a column without a native {@code getXxx} accessor: it routes through
     * {@link #getObject(ResultSet, int, Class)} so dialects can adapt the mapping
     * (e.g. DuckDB reads instants through {@link java.sql.Timestamp}).
     *
     * @param index 0-based column index, negative indexes read {@code null}.
     * @param type  java type to map the column to.
     * @param <T>   java type.
     */
    default <T> SQLFunction<ResultSet, T> reader(final int index, final Class<T> type) {
        if (index < 0) {
            return r -> null;
        }
        final var idx = index + 1;// translate list index to jdbc index
        return r -> type.cast(getObject(r, idx, type));
    }
}
