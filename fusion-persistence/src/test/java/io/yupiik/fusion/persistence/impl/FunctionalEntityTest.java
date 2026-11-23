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

import io.yupiik.fusion.persistence.api.ContextLessDatabase;
import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.api.SQLFunction;
import io.yupiik.fusion.persistence.impl.translation.DefaultTranslation;
import io.yupiik.fusion.persistence.spi.DatabaseTranslation;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FunctionalEntityTest {

    @Test
    void factoryReturnsEntityUsingSuppliedConfiguration() {
        final var configuration = configuration("-factory", new DefaultTranslation());
        final Entity.Factory<First, Integer> factory = (config, container) -> {
            assertSame(configuration, config);
            assertNull(container);
            return first(config);
        };
        final Entity<First, Integer> entity = factory.create(configuration, null);
        assertEquals(First.class, entity.getRootType());
        assertEquals("INSERT INTO first (name, day) VALUES (?, ?)", entity.getBatchInsertQuery());
        assertEquals("SELECT id, name, day FROM first WHERE id = ?", entity.getFindByIdQuery());
    }

    @Test
    void nullableJdbcBridgesKeepNativeSettersNullTypesAndExceptionIdentity() throws SQLException {
        final var calls = new java.util.ArrayList<List<Object>>();
        final var statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    calls.add(List.of(method.getName(), args[0], args[1]));
                    return null;
                });
        bindNativeValues(statement, false);
        assertEquals(List.of(java.sql.Types.VARCHAR, java.sql.Types.VARCHAR, java.sql.Types.INTEGER,
                        java.sql.Types.BIGINT, java.sql.Types.DOUBLE, java.sql.Types.FLOAT, java.sql.Types.BOOLEAN,
                        java.sql.Types.SMALLINT, java.sql.Types.SMALLINT, java.sql.Types.DATE,
                        java.sql.Types.DECIMAL, java.sql.Types.VARBINARY),
                calls.stream().map(it -> it.get(2)).toList());
        for (int i = 0; i < calls.size(); i++) {
            assertEquals("setNull", calls.get(i).get(0));
            assertEquals(i + 1, calls.get(i).get(1));
        }
        calls.clear();
        bindNativeValues(statement, true);
        assertEquals(List.of("setString", "setString", "setInt", "setLong", "setDouble", "setFloat",
                        "setBoolean", "setByte", "setShort", "setDate", "setBigDecimal", "setBytes"),
                calls.stream().map(it -> it.get(0)).toList());
        assertEquals("RUNNABLE", calls.get(1).get(2));
        assertEquals(2, calls.get(2).get(2));
        final var failure = new SQLException("native setter");
        for (int i = 1; i <= 12; i++) {
            final int index = i;
            final var failing = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                        if (Integer.valueOf(index).equals(args[0])) {
                            throw failure;
                        }
                        return null;
                    });
            assertSame(failure, assertThrows(SQLException.class, () -> bindNativeValues(failing, false)));
            assertSame(failure, assertThrows(SQLException.class, () -> bindNativeValues(failing, true)));
        }
    }

    private void bindNativeValues(final PreparedStatement statement, final boolean populated) throws SQLException {
        FunctionalEntity.bindString(statement, 1, populated ? "value" : null);
        FunctionalEntity.bindEnum(statement, 2, populated ? Thread.State.RUNNABLE : null);
        FunctionalEntity.bindInt(statement, 3, populated ? 2 : null);
        FunctionalEntity.bindLong(statement, 4, populated ? 3L : null);
        FunctionalEntity.bindDouble(statement, 5, populated ? 4.5 : null);
        FunctionalEntity.bindFloat(statement, 6, populated ? 5.5f : null);
        FunctionalEntity.bindBoolean(statement, 7, populated ? true : null);
        FunctionalEntity.bindByte(statement, 8, populated ? (byte) 6 : null);
        FunctionalEntity.bindShort(statement, 9, populated ? (short) 7 : null);
        FunctionalEntity.bindDate(statement, 10, populated ? Date.valueOf("2026-01-02") : null);
        FunctionalEntity.bindBigDecimal(statement, 11, populated ? BigDecimal.TEN : null);
        FunctionalEntity.bindBytes(statement, 12, populated ? new byte[]{8} : null);
    }

    @Test
    void directCallbacksPreserveCheckedAndUncheckedFailures() throws SQLException {
        final var configuration = configuration("-direct", new DefaultTranslation());
        final var failure = new SQLException("direct failure");
        final FunctionalEntity.Callback<First, PreparedStatement, First> callback = (context, value, statement) -> {
            assertSame(configuration, context.configuration());
            throw failure;
        };
        final var entity = new FunctionalEntity<First, Integer>(new FunctionalEntity.Context(configuration, null), First.class, "first", List.of(
                new ColumnMetadataImpl("id", Integer.class, "id", 0, false)), false,
                callback, callback, callback, (context, value, statement) -> { throw failure; },
                (context, value, keys) -> { throw failure; },
                (context, columns, ignored) -> { throw failure; },
                (context, projection, row) -> { throw failure; });
        final var value = new First(1, "value", null);
        assertSame(failure, assertThrows(SQLException.class, () -> entity.onInsert(value, null)));
        assertSame(failure, assertThrows(SQLException.class, () -> entity.onUpdate(value, null)));
        assertSame(failure, assertThrows(SQLException.class, () -> entity.onDelete(value, null)));
        assertSame(failure, assertThrows(SQLException.class, () -> entity.onFindById(1, null)));
        assertSame(failure, assertThrows(SQLException.class, () -> entity.onAfterInsert(value, (ResultSet) null)));
        assertSame(failure, assertThrows(PersistenceException.class, () -> entity.mapper(List.of())).getCause());
        final var unchecked = new IllegalStateException("not SQL");
        final Entity.Factory<First, Integer> factory = (config, container) -> { throw unchecked; };
        assertSame(unchecked, assertThrows(IllegalStateException.class, () -> factory.create(configuration, null)));
    }

    @Test
    void sqlBindingsKeysAndCallbacksForTwoModels() throws SQLException {
        final var configuration = configuration("-one", new DefaultTranslation());
        final var first = first(configuration);
        final var second = second(configuration);
        configuration.setInstanceLookup(type -> {
            if (type == First.class) {
                return first;
            }
            if (type == Second.class) {
                return second;
            }
            return "-one";
        });
        assertSame(first.getClass(), second.getClass());
        assertEquals("INSERT INTO first (name, day) VALUES (?, ?)", first.getBatchInsertQuery());
        assertEquals(first.getBatchInsertQuery(), first.getSingularInsertQuery());
        assertEquals("UPDATE first SET name = ?, day = ? WHERE id = ?", first.getUpdateQuery());
        assertEquals("SELECT tenant, id, name FROM second WHERE tenant = ? AND id = ?", second.getFindByIdQuery());
        assertEquals("UPDATE second SET name = ? WHERE tenant = ? AND id = ?", second.getUpdateQuery());
        assertEquals("INSERT INTO second (tenant, id, name) VALUES (?, ?, ?)", second.getBatchInsertQuery());
        assertFalse(second.isAutoIncremented());

        try (final var connection = configuration.getDataSource().getConnection();
             final var ddl = connection.createStatement()) {
            ddl.execute("create table first (id integer generated always as identity primary key, name varchar(100), day date)");
            ddl.execute("create table second (tenant varchar(50), id integer, name varchar(100), primary key(tenant, id))");
            final var database = (ContextLessDatabase) Database.of(configuration);
            final var inserted = database.insert(connection, new First(null, "insert", LocalDate.of(2026, 1, 2)));
            assertEquals(new First(1, "insert-one", LocalDate.of(2026, 1, 2)), inserted);
            final var other = database.insert(connection, new Second("tenant", 12, "other"));
            assertEquals(new Second("tenant", 12, "other-one"), other);

            try (final var statement = connection.prepareStatement(first.getFindByIdQuery())) {
                first.onFindById(inserted.id(), statement);
                try (final var row = statement.executeQuery()) {
                    row.next();
                    assertEquals(new First(1, "insert-one-load-one", inserted.day()), first.mapper(row).apply(row));
                }
            }
            try (final var statement = connection.prepareStatement(second.getFindByIdQuery())) {
                second.onFindById(new Key("tenant", 12), statement);
                try (final var row = statement.executeQuery()) {
                    row.next();
                    assertEquals(new Second("tenant", 12, "other-one-load-one"), second.mapper(row).apply(row));
                }
            }
            try (final var statement = connection.prepareStatement(second.getUpdateQuery())) {
                assertEquals(new Second("tenant", 12, "updated-update-one"),
                        second.onUpdate(new Second("tenant", 12, "updated"), statement));
                assertEquals(1, statement.executeUpdate());
            }
            try (final var statement = connection.prepareStatement("select name from second where tenant = 'tenant' and id = 12");
                 final var row = statement.executeQuery()) {
                row.next();
                assertEquals("updated-update-one", row.getString(1));
            }
            try (final var statement = connection.prepareStatement(first.getUpdateQuery())) {
                assertEquals(new First(1, "changed-update-one", null), first.onUpdate(new First(1, "changed", null), statement));
                assertEquals(1, statement.executeUpdate());
            }
            try (final var statement = connection.prepareStatement(second.getDeleteQuery())) {
                second.onDelete(other, statement);
                assertEquals(1, statement.executeUpdate());
            }
            try (final var statement = connection.prepareStatement(first.getDeleteQuery())) {
                first.onDelete(inserted, statement);
                assertEquals(1, statement.executeUpdate());
            }
        }
    }

    @Test
    void projectionsAndTranslationAreIsolatedPerMapperAndContainer() throws SQLException {
        final var left = configuration("-left", new ShiftedDateTranslation(1));
        final var right = configuration("-right", new ShiftedDateTranslation(2));
        final var leftEntity = first(left);
        final var rightEntity = first(right);
        final var leftMapper = leftEntity.mapFromPrefix("p", List.of("ignored", "pday", "pname", "pid"));
        final var rightMapper = rightEntity.mapper(List.of("day", "name", "id"));
        final var partial = leftEntity.mapper(List.of("name"));
        assertSame(leftMapper.getClass(), rightMapper.getClass());
        assertSame(leftMapper.getClass(), second(left).mapper(List.of("tenant", "id", "name")).getClass());
        try (final var connection = left.getDataSource().getConnection();
             final var statement = connection.createStatement()) {
            try (final var row = statement.executeQuery("select 999 as ignored, date '2026-01-02' as pday, 'value' as pname, 7 as pid")) {
                row.next();
                assertEquals(new First(7, "value-load-left", LocalDate.of(2026, 1, 3)), leftMapper.apply(row));
            }
            try (final var row = statement.executeQuery("select date '2026-01-02' as day, 'value' as name, 8 as id")) {
                row.next();
                assertEquals(new First(8, "value-load-right", LocalDate.of(2026, 1, 4)), rightMapper.apply(row));
            }
            try (final var row = statement.executeQuery("select 'partial' as name")) {
                row.next();
                assertEquals(new First(null, "partial-load-left", null), partial.apply(row));
            }
            // Reuse an earlier mapper after the other configuration/projections were exercised.
            try (final var row = statement.executeQuery("select 0, date '2026-02-01', 'again', 9")) {
                row.next();
                assertEquals(new First(9, "again-load-left", LocalDate.of(2026, 2, 2)), leftMapper.apply(row));
            }
        }
    }

    @Test
    void returningAndCheckedExceptionBoundaries() throws SQLException {
        final var configuration = configuration("", new ReturningTranslation());
        final var entity = first(configuration);
        assertEquals("INSERT INTO first (name, day) VALUES (?, ?) RETURNING id", entity.getSingularInsertQuery());
        assertEquals("INSERT INTO first (name, day) VALUES (?, ?)", entity.getBatchInsertQuery());
        final var plain = new First(null, "value", null);
        try (final var connection = configuration.getDataSource().getConnection();
             final var statement = connection.createStatement();
             final var row = statement.executeQuery("select 42")) {
            row.next();
            assertEquals(new First(42, "value", null), entity.onAfterInsert(plain, row));
        }
        assertSame(plain, entity.onAfterInsert(plain, (ResultSet) null));
        final var prepareError = assertThrows(PersistenceException.class, () -> entity.mapper(List.of("fail")));
        assertInstanceOf(SQLException.class, prepareError.getCause());
        assertEquals("prepare", prepareError.getCause().getMessage());
        final var mapper = entity.mapper(List.of("id"));
        final var readError = assertThrows(PersistenceException.class, () -> mapper.apply(null));
        assertInstanceOf(SQLException.class, readError.getCause());
        assertEquals("row", readError.getCause().getMessage());
        final var bindError = assertThrows(SQLException.class, () -> entity.onFindById(-1, null));
        assertEquals("bind", bindError.getMessage());
    }

    @Test
    void databaseUsesReturningResultSetForCompactEntity() throws SQLException {
        final var configuration = configuration("-returning", new ReturningTranslation());
        final var entity = first(configuration);
        configuration.setInstanceLookup(type -> type == First.class ? entity : "-returning");
        // H2 has no INSERT RETURNING; keep a real key ResultSet and emulate only this driver's statement.
        try (final var connection = configuration.getDataSource().getConnection();
             final var query = connection.createStatement();
             final var keys = query.executeQuery("select 42")) {
            final var statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "executeQuery" -> keys;
                        case "setString" -> {
                            assertEquals(1, args[0]);
                            assertEquals("value-returning", args[1]);
                            yield null;
                        }
                        case "setNull" -> {
                            assertEquals(2, args[0]);
                            yield null;
                        }
                        case "close" -> null;
                        default -> throw new AssertionError("Unexpected statement call: " + method.getName());
                    });
            final var driver = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            assertEquals(entity.getSingularInsertQuery(), args[0]);
                            return statement;
                        }
                        throw new AssertionError("Unexpected connection call: " + method.getName());
                    });
            final var database = (ContextLessDatabase) Database.of(configuration);
            assertEquals(new First(42, "value-returning", null), database.insert(driver, new First(null, "value", null)));
        }
    }

    @Test
    void publicReadersKeepLegacyProjectionDefaults() throws SQLException {
        assertEquals(0, FunctionalEntity.intOf(-1, false).apply(null));
        assertNull(FunctionalEntity.intOf(-1, true).apply(null));
        assertEquals(0L, FunctionalEntity.longOf(-1, false).apply(null));
        assertNull(FunctionalEntity.longOf(-1, true).apply(null));
        assertEquals(0., FunctionalEntity.doubleOf(-1, false).apply(null));
        assertNull(FunctionalEntity.doubleOf(-1, true).apply(null));
        assertEquals(0.f, FunctionalEntity.floatOf(-1, false).apply(null));
        assertNull(FunctionalEntity.floatOf(-1, true).apply(null));
        assertEquals(false, FunctionalEntity.booleanOf(-1, false).apply(null));
        assertNull(FunctionalEntity.booleanOf(-1, true).apply(null));
        assertEquals((byte) 0, FunctionalEntity.byteOf(-1, false).apply(null));
        assertNull(FunctionalEntity.byteOf(-1, true).apply(null));
        assertNull(FunctionalEntity.stringOf(-1).apply(null));
        assertNull(FunctionalEntity.enumOf(-1, Status.class).apply(null));
        assertNull(FunctionalEntity.dateOf(-1).apply(null));
        assertNull(FunctionalEntity.bigdecimalOf(-1).apply(null));
        assertNull(FunctionalEntity.bytesOf(-1).apply(null));
        final var configuration = configuration("", new DefaultTranslation());
        try (final var connection = configuration.getDataSource().getConnection();
             final var statement = connection.createStatement();
             final var row = statement.executeQuery("select 7, ' READY ', date '2026-01-02', 12.5, X'0102', null")) {
            row.next();
            assertEquals(7, FunctionalEntity.intOf(0, false).apply(row));
            assertEquals(7L, FunctionalEntity.longOf(0, true).apply(row));
            assertEquals(7., FunctionalEntity.doubleOf(0, false).apply(row));
            assertEquals(7.f, FunctionalEntity.floatOf(0, true).apply(row));
            assertEquals(true, FunctionalEntity.booleanOf(0, false).apply(row));
            assertEquals((byte) 7, FunctionalEntity.byteOf(0, true).apply(row));
            assertEquals(" READY ", FunctionalEntity.stringOf(1).apply(row));
            assertEquals(Status.READY, FunctionalEntity.enumOf(1, Status.class).apply(row));
            assertEquals(Date.valueOf("2026-01-02"), FunctionalEntity.dateOf(2).apply(row));
            assertEquals(new BigDecimal("12.5"), FunctionalEntity.bigdecimalOf(3).apply(row));
            assertArrayEquals(new byte[]{1, 2}, FunctionalEntity.bytesOf(4).apply(row));
            // Deliberately preserve the existing boxed-native SQL NULL behavior.
            assertEquals(0, FunctionalEntity.intOf(5, true).apply(row));
        }
    }

    private enum Status {
        READY
    }

    private DatabaseConfiguration configuration(final String suffix, final DatabaseTranslation translation) {
        final var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:compact_" + UUID.randomUUID() + ";NON_KEYWORDS=DAY,SECOND");
        final var configuration = new DatabaseConfiguration();
        configuration.setDataSource(source).setTranslation(translation).setInstanceLookup(type -> suffix);
        return configuration;
    }

    private Entity<First, Integer> first(final DatabaseConfiguration configuration) {
        return new FunctionalEntity<>(new FunctionalEntity.Context(configuration, null), First.class, "first", List.of(
                new ColumnMetadataImpl("id", Integer.class, "id", 0, true),
                new ColumnMetadataImpl("name", String.class, "name"),
                new ColumnMetadataImpl("day", LocalDate.class, "day")), true,
                (context, value, statement) -> {
                    final var result = new First(value.id(), value.name() + context.configuration().getInstanceLookup().apply(String.class), value.day());
                    statement.setString(1, result.name());
                    context.configuration().getTranslation().bind(statement, 2, result.day(), LocalDate.class);
                    return result;
                },
                (context, value, statement) -> {
                    final var result = new First(value.id(), value.name() + "-update" + context.configuration().getInstanceLookup().apply(String.class), value.day());
                    statement.setString(1, result.name());
                    context.configuration().getTranslation().bind(statement, 2, result.day(), LocalDate.class);
                    statement.setInt(3, result.id());
                    return result;
                },
                (context, value, statement) -> { statement.setInt(1, value.id()); return null; },
                (context, id, statement) -> {
                    if (Integer.valueOf(-1).equals(id)) {
                        throw new SQLException("bind");
                    }
                    statement.setInt(1, id);
                    return null;
                },
                (context, value, keys) -> keys == null ? value : new First(keys.getInt(1), value.name(), value.day()),
                FunctionalEntityTest::projection,
                (context, state, row) -> {
                    if (row == null) {
                        throw new SQLException("row");
                    }
                    final var name = state.name() < 0 ? null : row.getString(state.name() + 1);
                    return new First(state.id() < 0 ? null : row.getInt(state.id() + 1),
                            name + "-load" + context.configuration().getInstanceLookup().apply(String.class), state.day().apply(row));
                });
    }

    private Entity<Second, Key> second(final DatabaseConfiguration configuration) {
        return new FunctionalEntity<>(new FunctionalEntity.Context(configuration, null), Second.class, "second", List.of(
                new ColumnMetadataImpl("tenant", String.class, "tenant", 0, false),
                new ColumnMetadataImpl("id", int.class, "id", 1, false),
                new ColumnMetadataImpl("name", String.class, "name")), false,
                (context, value, statement) -> {
                    final var result = new Second(value.tenant(), value.id(), value.name() + context.configuration().getInstanceLookup().apply(String.class));
                    statement.setString(1, result.tenant());
                    statement.setInt(2, result.id());
                    statement.setString(3, result.name());
                    return result;
                },
                (context, value, statement) -> {
                    final var result = new Second(value.tenant(), value.id(), value.name() + "-update" + context.configuration().getInstanceLookup().apply(String.class));
                    statement.setString(1, result.name());
                    statement.setString(2, result.tenant());
                    statement.setInt(3, result.id());
                    return result;
                },
                (context, value, statement) -> {
                    statement.setString(1, value.tenant());
                    statement.setInt(2, value.id());
                    return null;
                },
                (context, key, statement) -> {
                    statement.setString(1, key.tenant());
                    statement.setInt(2, key.id());
                    return null;
                }, null, FunctionalEntityTest::projection,
                (context, state, row) -> {
                    if (row == null) {
                        throw new SQLException("row");
                    }
                    final var name = state.name() < 0 ? null : row.getString(state.name() + 1);
                    return new Second(state.tenant() < 0 ? null : row.getString(state.tenant() + 1),
                            state.id() < 0 ? 0 : row.getInt(state.id() + 1),
                            name + "-load" + context.configuration().getInstanceLookup().apply(String.class));
                });
    }

    private record First(Integer id, String name, LocalDate day) {
    }

    private record Second(String tenant, int id, String name) {
    }

    private record Key(String tenant, int id) {
    }

    private record Projection(int id, int name, int tenant, SQLFunction<ResultSet, LocalDate> day) {
    }

    private static Projection projection(final FunctionalEntity.Context context, final List<String> columns,
                                         final Void ignored) throws SQLException {
        if (columns.contains("fail")) {
            throw new SQLException("prepare");
        }
        return new Projection(columns.indexOf("id"), columns.indexOf("name"), columns.indexOf("tenant"),
                context.configuration().getTranslation().reader(columns.indexOf("day"), LocalDate.class));
    }

    private static final class ShiftedDateTranslation extends DefaultTranslation {
        private final int days;

        private ShiftedDateTranslation(final int days) {
            this.days = days;
        }

        @Override
        public Object getObject(final ResultSet row, final int index, final Class<?> type) throws SQLException {
            final var value = super.getObject(row, index, type);
            return value instanceof LocalDate date ? date.plusDays(days) : value;
        }
    }

    private static final class ReturningTranslation extends DefaultTranslation {
        @Override
        public boolean supportsGeneratedKeys() {
            return false;
        }
    }
}
