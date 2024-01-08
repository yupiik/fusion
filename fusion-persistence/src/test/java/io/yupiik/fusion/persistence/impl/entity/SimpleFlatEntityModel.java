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
package io.yupiik.fusion.persistence.impl.entity;

import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.impl.BaseEntity;
import io.yupiik.fusion.persistence.impl.ColumnMetadataImpl;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;

import java.sql.SQLException;
import java.util.List;

// this is what will generate the annotation processor
public class SimpleFlatEntityModel extends BaseEntity<SimpleFlatEntity, String> {
    public SimpleFlatEntityModel(final DatabaseConfiguration database) {
        super(database,
                SimpleFlatEntity.class,
                "SIMPLE_FLAT_ENTITY",
                "select id, name, SIMPLE_AGE from SIMPLE_FLAT_ENTITY where id = ?",
                "update SIMPLE_FLAT_ENTITY set name = ?, SIMPLE_AGE = ? where id = ?",
                "delete from SIMPLE_FLAT_ENTITY where id = ?",
                "insert into SIMPLE_FLAT_ENTITY (id, name, SIMPLE_AGE) values (?, ?, ?)",
                "select id, name, SIMPLE_AGE from SIMPLE_FLAT_ENTITY",
                "select count(*) from SIMPLE_FLAT_ENTITY",
                List.of(
                        new ColumnMetadataImpl("id", String.class, "id", 0, false),
                        new ColumnMetadataImpl("age", int.class, "SIMPLE_AGE"),
                        new ColumnMetadataImpl("name", String.class, "name")),
                false,
                (instance, statement) -> {
                    final var inst = instance.onInsert();
                    statement.setString(1, inst.id());
                    statement.setString(2, inst.name());
                    statement.setInt(3, inst.age());
                    return inst;
                },
                (instance, statement) -> {
                    statement.setString(1, instance.name());
                    statement.setInt(2, instance.age());
                    statement.setString(3, instance.id());
                    return instance;
                },
                (instance, statement) -> statement.setString(1, instance.id()),
                (id, statement) -> statement.setString(1, id),
                (usedInstance, p) -> usedInstance,
                columns -> {
                    // /!\ lowercased!
                    final var id = stringOf(columns.indexOf("id"));
                    final var name = stringOf(columns.indexOf("name"));
                    final var age = intOf(columns.indexOf("simple_age"), false);
                    return rset -> {
                        try {
                            var instance = new SimpleFlatEntity(id.apply(rset), name.apply(rset), age.apply(rset));
                            instance = instance.onLoad();
                            return instance;
                        } catch (final SQLException e) {
                            throw new PersistenceException(e);
                        }
                    };
                });
    }
}