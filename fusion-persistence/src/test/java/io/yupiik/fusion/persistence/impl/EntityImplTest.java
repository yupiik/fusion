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
import io.yupiik.fusion.persistence.impl.entity.SimpleFlatEntity;
import io.yupiik.fusion.persistence.impl.entity.SimpleFlatEntityModel;
import io.yupiik.fusion.persistence.test.EnableH2;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

// note: this test does not validate the entity model generation but just the runtime
class EntityImplTest {
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
}
