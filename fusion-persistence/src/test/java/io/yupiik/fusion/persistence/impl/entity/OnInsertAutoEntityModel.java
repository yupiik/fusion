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

import io.yupiik.fusion.persistence.impl.BaseEntity;
import io.yupiik.fusion.persistence.impl.ColumnMetadataImpl;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

// mirrors what the annotation processor generates for an auto-incremented entity with an @OnInsert callback
public class OnInsertAutoEntityModel extends BaseEntity<OnInsertAutoEntity, Long> {
    public OnInsertAutoEntityModel(final DatabaseConfiguration database) {
        super(database,
                OnInsertAutoEntity.class,
                "ON_INSERT_AUTO_ENTITY",
                "select id, name, epoch from ON_INSERT_AUTO_ENTITY where id = ?",
                "update ON_INSERT_AUTO_ENTITY set name = ?, epoch = ? where id = ?",
                "delete from ON_INSERT_AUTO_ENTITY where id = ?",
                "insert into ON_INSERT_AUTO_ENTITY (name, epoch) values (?, ?)",
                "select id, name, epoch from ON_INSERT_AUTO_ENTITY",
                "select count(*) from ON_INSERT_AUTO_ENTITY",
                List.of(
                        new ColumnMetadataImpl("id", long.class, "id", 0, true),
                        new ColumnMetadataImpl("name", String.class, "name"),
                        new ColumnMetadataImpl("epoch", long.class, "epoch")),
                true,
                (entity, statement) -> {
                    // @OnInsert callback result is what gets bound and passed on to onAfterInsert
                    final var instance = entity.onInsert();
                    statement.setString(1, instance.name());
                    statement.setLong(2, instance.epoch());
                    return instance;
                },
                (instance, statement) -> {
                    statement.setString(1, instance.name());
                    statement.setLong(2, instance.epoch());
                    statement.setLong(3, instance.id());
                    return instance;
                },
                (instance, statement) -> statement.setLong(1, instance.id()),
                (id, statement) -> statement.setLong(1, id),
                (usedInstance, rset) -> new OnInsertAutoEntity(rset.getLong(1), usedInstance.name(), usedInstance.epoch()),
                columns -> {
                    final var id = longOf(columns.indexOf("id"), false);
                    final var name = stringOf(columns.indexOf("name"));
                    final var epoch = longOf(columns.indexOf("epoch"), false);
                    final Function<java.sql.ResultSet, OnInsertAutoEntity> mapping;
                    mapping = rset -> {
                        try {
                            return new OnInsertAutoEntity(id.apply(rset), name.apply(rset), epoch.apply(rset));
                        } catch (final SQLException e) {
                            throw new io.yupiik.fusion.persistence.api.PersistenceException(e);
                        }
                    };
                    return mapping;
                });
    }
}