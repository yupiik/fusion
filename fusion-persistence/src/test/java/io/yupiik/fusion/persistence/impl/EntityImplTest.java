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

import io.yupiik.fusion.framework.build.api.persistence.Column;
import io.yupiik.fusion.framework.build.api.persistence.Id;
import io.yupiik.fusion.framework.build.api.persistence.Table;
import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.test.EnableH2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

// note: this test does not validate the entity model generation but just the runtime
class EntityImplTest {
    @Test
    @EnableH2
    @Disabled("not yet impl: todo: review it is relevant since it should go in annotation processor module where the magic happens")
    void enumField(final DataSource dataSource) throws SQLException {
        final var database = Database.of(newDatabaseConfiguration(dataSource));
        final var model = database.getOrCreateEntity(WithEnum.class);
        assertEquals("id, type", model.concatenateColumns(new Entity.ColumnsConcatenationRequest()));
        assertEquals(List.of("CREATE TABLE WITH_ENUM (id VARCHAR(255), type VARCHAR(255), PRIMARY KEY (id))"), List.of(model.ddl()));
        assertEquals(MyType.class, model.getOrderedColumns().stream()
                .filter(it -> "type".equals(it.javaName()))
                .findFirst()
                .orElseThrow(AssertionError::new)
                .type());
        try (final var connection = dataSource.getConnection()) {
            try (final var stmt = connection.createStatement()) {
                stmt.execute("create table with_enum (id varchar(255), type varchar(5), primary key(id))");
            }

            final var entity = new WithEnum();
            entity.id = "1";
            entity.type = MyType.OTHER;
            database.insert(entity);
            assertEquals(entity, database.findById(WithEnum.class, "1"));

            entity.type = null;
            database.update(entity);
            assertEquals(entity, database.findById(WithEnum.class, "1"));
        }
    }

    @Test
    @EnableH2
    void concatenateFields(final DataSource dataSource) {
        final var database = Database.of(newDatabaseConfiguration(dataSource));
        final var entity = database.getOrCreateEntity(SimpleFlatEntity.class);
        assertEquals("e.id as eId, e.name as eName", entity.concatenateColumns(new Entity.ColumnsConcatenationRequest().setPrefix("e.").setAliasPrefix("e").setIgnored(Set.of("age"))));
        assertEquals("a.id as aId, a.SIMPLE_AGE as aAge, a.name as aName", entity.concatenateColumns(new Entity.ColumnsConcatenationRequest().setAliasPrefix("a").setPrefix("a.")));
        assertEquals("a.id, a.SIMPLE_AGE, a.name", entity.concatenateColumns(new Entity.ColumnsConcatenationRequest().setPrefix("a.")));
        assertEquals("a.id as id, a.SIMPLE_AGE as age, a.name as name", entity.concatenateColumns(new Entity.ColumnsConcatenationRequest().setPrefix("a.").setAliasPrefix("")));
    }

    @Test
    @EnableH2
    void metadata(final DataSource dataSource) {
        final var configuration = newDatabaseConfiguration(dataSource);
        final var entity = Database.of(configuration).getOrCreateEntity(SimpleFlatEntity.class);
        assertEquals(
                List.of("id (java.lang.String): id", "age (int): SIMPLE_AGE", "name (java.lang.String): name"),
                entity.getOrderedColumns().stream()
                        .map(c -> c.javaName() + " (" + c.type().getTypeName() + "): " + c.columnName())
                        .collect(toList()));
    }

    private DatabaseConfiguration newDatabaseConfiguration(final DataSource dataSource) {
        final var configuration = new DatabaseConfiguration();
        configuration
                .setDataSource(dataSource)
                .setInstanceLookup(k -> k == SimpleFlatEntity.class ? new SimpleFlatEntityModel(configuration) : null);
        return configuration;
    }

    // this is what will generate the annotation processor
    public static class SimpleFlatEntityModel extends BaseEntity<SimpleFlatEntity, String> {
        public SimpleFlatEntityModel(final DatabaseConfiguration database) {
            super(database,
                    new String[]{"create simple model"}, // not valid but ok for the test
                    SimpleFlatEntity.class,
                    "SIMPLE_FLAT_ENTITY",
                    "select id, name, SIMPLE_AGE from SIMPLE_FLAT_ENTITY where id = ?",
                    "update SIMPLE_FLAT_ENTITY set name = ?, SIMPLE_AGE = ? where id = ?",
                    "delete from SIMPLE_FLAT_ENTITY where id = ?",
                    "insert into SIMPLE_FLAT_ENTITY (id, name, SIMPLE_AGE) values (?, ?, ?)",
                    "select id, name, SIMPLE_AGE from SIMPLE_FLAT_ENTITY",
                    List.of(
                            new ColumnMetadataImpl("id", String.class, "id"),
                            new ColumnMetadataImpl("age", int.class, "SIMPLE_AGE"),
                            new ColumnMetadataImpl("name", String.class, "name")),
                    false,
                    (instance, statement) -> {
                        statement.setString(1, instance.id);
                        statement.setString(2, instance.name);
                        statement.setInt(3, instance.age);
                    },
                    (instance, statement) -> {
                        statement.setString(1, instance.name);
                        statement.setInt(2, instance.age);
                        statement.setString(3, instance.id);
                    },
                    (instance, statement) -> statement.setString(1, instance.id),
                    (id, statement) -> statement.setString(1, id),
                    (SQLBiFunction<SimpleFlatEntity, PreparedStatement, SimpleFlatEntity>) SQLBiFunction.IDENTITY,
                    columns -> {
                        final var id = stringOf(columns.indexOf("id"));
                        final var name = stringOf(columns.indexOf("name"));
                        final var age = intOf(columns.indexOf("name"), false);
                        return rset -> {
                            try {
                                return new SimpleFlatEntity(id.apply(rset), name.apply(rset), age.apply(rset));
                            } catch (final SQLException e) {
                                throw new PersistenceException(e);
                            }
                        };
                    });
        }
    }

    // @Table("SIMPLE_FLAT_ENTITY")
    public record SimpleFlatEntity(
            /*@Id*/ String id,
            /*@Column*/ String name,
            /* @Column(name = "SIMPLE_AGE")*/ int age) {
    }

    // todo
    @Table("WITH_ENUM")
    public static class WithEnum {
        @Id
        private String id;

        @Column
        private MyType type;

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final WithEnum withEnum = WithEnum.class.cast(o);
            return id.equals(withEnum.id) && type == withEnum.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, type);
        }
    }

    public enum MyType {
        A, OTHER
    }
}
