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
package io.yupiik.fusion.persistence.impl.translation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

/**
 * DuckDB dialect.
 *
 * <p>Differences with the default translation:</p>
 * <ul>
 *     <li>{@code getGeneratedKeys()} is not implemented by the DuckDB JDBC driver, generated keys
 *     are read back through a RETURNING clause instead;</li>
 *     <li>{@code setObject(Instant)} / {@code getObject(column, Instant.class)} are not supported by the driver,
 *     instants are bound and read through {@link Timestamp} mediation.</li>
 * </ul>
 */
public class DuckDBTranslation extends DefaultTranslation {
    @Override
    public boolean supportsGeneratedKeys() {
        return false;
    }

    @Override
    public void bind(final PreparedStatement statement, final int index, final Object value,
                     final Class<?> declaredType) throws SQLException {
        if (value instanceof Instant instant) {
            statement.setTimestamp(index, Timestamp.from(instant));
        } else {
            super.bind(statement, index, value, declaredType);
        }
    }

    @Override
    public void bindNull(final PreparedStatement statement, final int index, final Class<?> type) throws SQLException {
        if (type == Instant.class) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            super.bindNull(statement, index, type);
        }
    }

    @Override
    public Object getObject(final ResultSet resultSet, final int index, final Class<?> type) throws SQLException {
        return type == Instant.class ? toInstant(resultSet.getTimestamp(index)) : super.getObject(resultSet, index, type);
    }

    @Override
    public Object getObject(final ResultSet resultSet, final String column, final Class<?> type) throws SQLException {
        return type == Instant.class ? toInstant(resultSet.getTimestamp(column)) : super.getObject(resultSet, column, type);
    }

    private static Instant toInstant(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
