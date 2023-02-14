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

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.build.api.persistence.Column;
import io.yupiik.fusion.framework.build.api.persistence.Id;
import io.yupiik.fusion.framework.build.api.persistence.Table;
import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.test.EnableH2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityImplTest {
    @Test
    @EnableH2
    void enumField(final DataSource dataSource, final RuntimeContainer container) throws SQLException {
        final var database = Database.of(new DatabaseConfiguration().setDataSource(dataSource), container);
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
    void concatenateFields(final DataSource dataSource, final RuntimeContainer container) {
        final var database = Database.of(new DatabaseConfiguration().setDataSource(dataSource), container);
        final var entity = database.getOrCreateEntity(SimpleFlatEntity.class);
        assertEquals("e.id as eId, e.name as eName", entity.concatenateColumns(new Entity.ColumnsConcatenationRequest().setPrefix("e.").setAliasPrefix("e").setIgnored(Set.of("age"))));
        assertEquals("a.id as aId, a.SIMPLE_AGE as aAge, a.name as aName", entity.concatenateColumns(new Entity.ColumnsConcatenationRequest().setAliasPrefix("a").setPrefix("a.")));
        assertEquals("a.id, a.SIMPLE_AGE, a.name", entity.concatenateColumns(new Entity.ColumnsConcatenationRequest().setPrefix("a.")));
        assertEquals("a.id as id, a.SIMPLE_AGE as age, a.name as name", entity.concatenateColumns(new Entity.ColumnsConcatenationRequest().setPrefix("a.").setAliasPrefix("")));
    }

    @Test
    @EnableH2
    void metadata(final DataSource dataSource, final RuntimeContainer container) {
        final var entity = Database.of(new DatabaseConfiguration().setDataSource(dataSource), container).getOrCreateEntity(SimpleFlatEntity.class);
        assertEquals(
                List.of("id (java.lang.String): id", "age (int): SIMPLE_AGE", "name (java.lang.String): name"),
                entity.getOrderedColumns().stream()
                        .map(c -> c.javaName() + " (" + c.type().getTypeName() + "): " + c.columnName())
                        .collect(toList()));
    }

    @Table("SIMPLE_FLAT_ENTITY")
    public static class SimpleFlatEntity {
        @Id
        private String id;

        @Column
        private String name;

        @Column(name = "SIMPLE_AGE")
        private int age;
    }

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
