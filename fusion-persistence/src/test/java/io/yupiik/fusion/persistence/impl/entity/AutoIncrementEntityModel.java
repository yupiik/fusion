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
package io.yupiik.fusion.persistence.impl.entity;

import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.impl.BaseEntity;
import io.yupiik.fusion.persistence.impl.ColumnMetadataImpl;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;

import java.sql.SQLException;
import java.util.List;

// this is what will generate the annotation processor
public class AutoIncrementEntityModel extends BaseEntity<AutoIncrementEntity, Long> {
    public AutoIncrementEntityModel(final DatabaseConfiguration database) {
        super(database,
                AutoIncrementEntity.class,
                "AUTO_INCREMENT_ENTITY",
                "select id, name from AUTO_INCREMENT_ENTITY where id = ?",
                "update AUTO_INCREMENT_ENTITY set name = ?, where id = ?",
                "delete from AUTO_INCREMENT_ENTITY where id = ?",
                "insert into AUTO_INCREMENT_ENTITY (name) values (?)",
                "select id, name from AUTO_INCREMENT_ENTITY",
                "select count(*) from AUTO_INCREMENT_ENTITY",
                List.of(
                        new ColumnMetadataImpl("id", String.class, "id", 0, true),
                        new ColumnMetadataImpl("name", String.class, "name")),
                true,
                (inst, statement) -> {
                    statement.setString(1, inst.name());
                    return inst;
                },
                (inst, statement) -> {
                    statement.setString(1, inst.name());
                    statement.setLong(2, inst.id());
                    return inst;
                },
                (instance, statement) -> statement.setLong(1, instance.id()),
                (id, statement) -> statement.setLong(1, id),
                (usedInstance, p) -> {
                    try (final var rset = p.getGeneratedKeys()) {
                        if (!rset.next()) {
                            throw new PersistenceException("No generated key available");
                        }
                        return new AutoIncrementEntity(rset.getLong(1), usedInstance.name());
                    }
                },
                columns -> {
                    final var id = longOf(columns.indexOf("id"), false);
                    final var name = stringOf(columns.indexOf("name"));
                    return rset -> {
                        try {
                            return new AutoIncrementEntity(id.apply(rset), name.apply(rset));
                        } catch (final SQLException e) {
                            throw new PersistenceException(e);
                        }
                    };
                });
    }
}