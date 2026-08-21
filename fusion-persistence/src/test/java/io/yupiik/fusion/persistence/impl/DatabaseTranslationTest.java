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

import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.impl.entity.AutoIncrementEntity;
import io.yupiik.fusion.persistence.impl.entity.AutoIncrementEntityModel;
import io.yupiik.fusion.persistence.impl.translation.DefaultTranslation;
import io.yupiik.fusion.persistence.impl.translation.DuckDBTranslation;
import io.yupiik.fusion.persistence.spi.DatabaseTranslation;
import io.yupiik.fusion.persistence.test.EnableH2;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTranslationTest {
    @Test // H2 does not support RETURNING: validates the branch with a fake driver implementing it
    void insertWithReturningBranch() throws SQLException {
        final var configuration = new DatabaseConfiguration();
        configuration
                .setDataSource((DataSource) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                        new Class<?>[]{DataSource.class},
                        (p, method, args) -> {
                            throw new UnsupportedOperationException("not used");
                        }))
                .setTranslation(new DefaultTranslation() {
                    @Override
                    public boolean supportsGeneratedKeys() {
                        return false;
                    }
                })
                .setInstanceLookup(k -> k == AutoIncrementEntity.class ? new AutoIncrementEntityModel(configuration) : null);
        final var database = (DatabaseImpl) Database.of(configuration);

        final var inserted = database.insert(fakeConnection(), new AutoIncrementEntity(0, "returned"));
        assertEquals(42, inserted.id());
    }

    @Test // default translation supports getGeneratedKeys(): validates the unified key-path through Statement#getGeneratedKeys
    void insertWithGeneratedKeysBranch() throws SQLException {
        final var configuration = new DatabaseConfiguration();
        configuration
                .setDataSource((DataSource) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                        new Class<?>[]{DataSource.class},
                        (p, method, args) -> {
                            throw new UnsupportedOperationException("not used");
                        }))
                .setTranslation(new DefaultTranslation())
                .setInstanceLookup(k -> k == AutoIncrementEntity.class ? new AutoIncrementEntityModel(configuration) : null);
        final var database = (DatabaseImpl) Database.of(configuration);

        final var inserted = database.insert(generatedKeysConnection(), new AutoIncrementEntity(0, "keys"));
        assertEquals(7, inserted.id());
    }

    @Test // RETURNING branch with no returned key must fail loudly through the unified onAfterInsert guard
    void insertWithReturningNoKeys() throws SQLException {
        final var configuration = new DatabaseConfiguration();
        configuration
                .setDataSource((DataSource) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                        new Class<?>[]{DataSource.class},
                        (p, method, args) -> {
                            throw new UnsupportedOperationException("not used");
                        }))
                .setTranslation(new DefaultTranslation() {
                    @Override
                    public boolean supportsGeneratedKeys() {
                        return false;
                    }
                })
                .setInstanceLookup(k -> k == AutoIncrementEntity.class ? new AutoIncrementEntityModel(configuration) : null);
        final var database = (DatabaseImpl) Database.of(configuration);

        final var error = assertThrows(PersistenceException.class,
                () -> database.insert(emptyKeysConnection(), new AutoIncrementEntity(0, "none")));
        assertEquals("Can't save " + new AutoIncrementEntity(0, "none"), error.getMessage());
    }

    @Test // generated-keys branch without any returned key must also fail loudly
    void insertWithGeneratedKeysNoKeys() throws SQLException {
        final var configuration = new DatabaseConfiguration();
        configuration
                .setDataSource((DataSource) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                        new Class<?>[]{DataSource.class},
                        (p, method, args) -> {
                            throw new UnsupportedOperationException("not used");
                        }))
                .setTranslation(new DefaultTranslation())
                .setInstanceLookup(k -> k == AutoIncrementEntity.class ? new AutoIncrementEntityModel(configuration) : null);
        final var database = (DatabaseImpl) Database.of(configuration);

        final var error = assertThrows(PersistenceException.class,
                () -> database.insert(emptyGeneratedKeysConnection(), new AutoIncrementEntity(0, "none")));
        assertEquals("No generated key available", error.getMessage());
    }

    @Test // Instant columns are mediated through java.sql.Timestamp, nulls included
    @EnableH2
    void instantRoundTrip(final DataSource dataSource) throws SQLException {
        final var translation = new DuckDBTranslation();
        try (final var connection = dataSource.getConnection();
             final var stmt = connection.createStatement()) {
            stmt.executeUpdate("create table TRANSLATION_TEST (ts TIMESTAMP)");
        }

        var instant = Instant.parse("2026-08-21T10:15:30.123456Z");
        try (final var connection = dataSource.getConnection();
             final var stmt = connection.prepareStatement("insert into TRANSLATION_TEST (ts) values (?)")) {
            translation.bind(stmt, 1, instant, Instant.class);
            assertEquals(1, stmt.executeUpdate());
        }
        try (final var connection = dataSource.getConnection();
             final var stmt = connection.prepareStatement("select ts from TRANSLATION_TEST");
             final var rset = stmt.executeQuery()) {
            assertTrue(rset.next());
            assertEquals(Instant.parse("2026-08-21T10:15:30.123456Z"), translation.reader(0, Instant.class).apply(rset));
        }

        try (final var connection = dataSource.getConnection();
             final var stmt = connection.prepareStatement("insert into TRANSLATION_TEST (ts) values (?)")) {
            translation.bind(stmt, 1, null, Instant.class);
            assertEquals(1, stmt.executeUpdate());
        }
        try (final var connection = dataSource.getConnection();
             final var stmt = connection.prepareStatement("select ts from TRANSLATION_TEST where ts is null");
             final var rset = stmt.executeQuery()) {
            assertTrue(rset.next());
            assertNull(translation.reader(0, Instant.class).apply(rset));
            assertFalse(rset.next());
        }
    }

    @Test // java.time types and BigInteger have no native accessor: they are all routed through the translation
    @EnableH2
    void defaultTranslationReaders(final DataSource dataSource) throws SQLException {
        final var translation = new DefaultTranslation();
        try (final var connection = dataSource.getConnection();
             final var stmt = connection.createStatement()) {
            stmt.executeUpdate("create table TRANSLATION_TYPES_TEST (" +
                    "bi BIGINT, ld DATE, ldt TIMESTAMP, lt TIME, odt TIMESTAMP WITH TIME ZONE, zdt TIMESTAMP WITH TIME ZONE)");
        }

        try (final var connection = dataSource.getConnection();
             final var stmt = connection.prepareStatement(
                     "insert into TRANSLATION_TYPES_TEST values (?, ?, ?, ?, ?, ?)")) {
            translation.bind(stmt, 1, BigInteger.valueOf(77), BigInteger.class);
            translation.bind(stmt, 2, LocalDate.parse("2026-08-21"), LocalDate.class);
            translation.bind(stmt, 3, LocalDateTime.parse("2026-08-21T10:15:30"), LocalDateTime.class);
            translation.bind(stmt, 4, LocalTime.parse("10:15:30"), LocalTime.class);
            translation.bind(stmt, 5, OffsetDateTime.parse("2026-08-21T10:15:30+02:00"), OffsetDateTime.class);
            translation.bind(stmt, 6, ZonedDateTime.parse("2026-08-21T10:15:30+02:00[Europe/Paris]"), ZonedDateTime.class);
            assertEquals(1, stmt.executeUpdate());
        }

        try (final var connection = dataSource.getConnection();
             final var stmt = connection.prepareStatement("select * from TRANSLATION_TYPES_TEST");
             final var rset = stmt.executeQuery()) {
            assertTrue(rset.next());
            assertEquals(BigInteger.valueOf(77), translation.reader(0, BigInteger.class).apply(rset));
            assertEquals(LocalDate.parse("2026-08-21"), translation.reader(1, LocalDate.class).apply(rset));
            assertEquals(LocalDateTime.parse("2026-08-21T10:15:30"), translation.reader(2, LocalDateTime.class).apply(rset));
            assertEquals(LocalTime.parse("10:15:30"), translation.reader(3, LocalTime.class).apply(rset));
            assertEquals(OffsetDateTime.parse("2026-08-21T10:15:30+02:00").toInstant(), translation.reader(4, OffsetDateTime.class).apply(rset).toInstant());
            assertEquals(ZonedDateTime.parse("2026-08-21T10:15:30+02:00[Europe/Paris]").toInstant(), translation.reader(5, ZonedDateTime.class).apply(rset).toInstant());
            assertFalse(rset.next());
        }
    }

    @Test // null params keep the SQL types that were bound by DatabaseImpl before the move to the translation
    void bindNullSqlTypes() throws SQLException {
        final var translation = new DefaultTranslation();
        final var sqlTypes = new int[]{Types.OTHER};
        final var statement = (PreparedStatement) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (p, method, args) -> {
                    if ("setNull".equals(method.getName())) {
                        sqlTypes[0] = (int) args[1];
                    }
                    return defaultValue(method.getReturnType());
                });

        translation.bind(statement, 1, null, OffsetDateTime.class);
        assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, sqlTypes[0]);

        translation.bind(statement, 1, null, ZonedDateTime.class);
        assertEquals(Types.TIMESTAMP_WITH_TIMEZONE, sqlTypes[0]);

        translation.bind(statement, 1, null, LocalTime.class);
        assertEquals(Types.TIME, sqlTypes[0]);
    }

    // minimal fakes simulating a driver supporting RETURNING (like DuckDB)
    private static Connection fakeConnection() {
        return (Connection) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (p, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        // the RETURNING branch must use the single-arg overload (DuckDB rejects the two-arg ones)
                        assertEquals(1, args.length);
                        assertEquals("insert into AUTO_INCREMENT_ENTITY (name) values (?) RETURNING id", args[0]);
                        return fakeStatement();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement fakeStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (p, method, args) -> {
                    if ("setString".equals(method.getName())) {
                        assertEquals("returned", args[1]);
                    }
                    return "executeQuery".equals(method.getName()) ? fakeResultSet() : defaultValue(method.getReturnType());
                });
    }

    private static ResultSet fakeResultSet() {
        final var reads = new int[1];
        return (ResultSet) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (p, method, args) -> {
                    if ("next".equals(method.getName())) {
                        return ++reads[0] == 1;
                    }
                    if ("getLong".equals(method.getName())) {
                        return 42L;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    // fakes simulating a driver supporting getGeneratedKeys()
    private static Connection generatedKeysConnection() {
        return (Connection) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (p, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        assertEquals(PreparedStatement.RETURN_GENERATED_KEYS, args[1]);
                        return generatedKeysStatement();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement generatedKeysStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (p, method, args) -> {
                    if ("setString".equals(method.getName())) {
                        assertEquals("keys", args[1]);
                    }
                    if ("executeUpdate".equals(method.getName())) {
                        return 1;
                    }
                    return "getGeneratedKeys".equals(method.getName()) ? generatedKeysResultSet() : defaultValue(method.getReturnType());
                });
    }

    private static ResultSet generatedKeysResultSet() {
        final var reads = new int[1];
        return (ResultSet) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (p, method, args) -> {
                    if ("next".equals(method.getName())) {
                        return ++reads[0] == 1;
                    }
                    if ("getLong".equals(method.getName())) {
                        return 7L;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    // RETURNING branch (supportsGeneratedKeys()==false) without any generated key returned
    private static Connection emptyKeysConnection() {
        return (Connection) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (p, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        // the RETURNING branch must use the single-arg overload (DuckDB rejects the two-arg ones)
                        assertEquals(1, args.length);
                        assertEquals("insert into AUTO_INCREMENT_ENTITY (name) values (?) RETURNING id", args[0]);
                        return (PreparedStatement) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                                new Class<?>[]{PreparedStatement.class},
                                (sp, m, sa) -> "executeQuery".equals(m.getName()) ? emptyResultSet() : defaultValue(m.getReturnType()));
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    // generated-keys branch (supportsGeneratedKeys()==true) without any generated key returned
    private static Connection emptyGeneratedKeysConnection() {
        return (Connection) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (p, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        assertEquals(PreparedStatement.RETURN_GENERATED_KEYS, args[1]);
                        return (PreparedStatement) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                                new Class<?>[]{PreparedStatement.class},
                                (sp, m, args2) -> {
                                    if ("executeUpdate".equals(m.getName())) {
                                        return 1;
                                    }
                                    return "getGeneratedKeys".equals(m.getName()) ? emptyResultSet() : defaultValue(m.getReturnType());
                                });
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ResultSet emptyResultSet() {
        return (ResultSet) Proxy.newProxyInstance(DatabaseTranslationTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (p, method, args) -> "next".equals(method.getName()) ? false : defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive() || void.class == type) {
            return null;
        }
        if (boolean.class == type) {
            return false;
        }
        if (long.class == type) {
            return 0L;
        }
        if (int.class == type) {
            return 0;
        }
        throw new UnsupportedOperationException("Unexpected primitive: " + type);
    }
}
