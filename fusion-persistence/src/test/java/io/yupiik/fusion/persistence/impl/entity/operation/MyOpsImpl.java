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
package io.yupiik.fusion.persistence.impl.entity.operation;

import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.api.ResultSetWrapper;
import io.yupiik.fusion.persistence.api.SQLFunction;
import io.yupiik.fusion.persistence.impl.entity.SimpleFlatEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static io.yupiik.fusion.persistence.api.StatementBinder.NONE;
import static java.util.stream.Collectors.joining;

// impl should be generated by the annot proc + registered as a bean of type MyOps (for injections)
public class MyOpsImpl implements MyOps {
    private final Database database;
    private volatile SQLFunction<ResultSet, SimpleFlatEntity> entityListMapper;

    public MyOpsImpl(final Database database) {
        this.database = database;
    }

    @Override
    public long countAll() {
        return database.query("select count(*) from SIMPLE_FLAT_ENTITY", NONE, r -> {
            if (r.hasNext()) {
                return r.map(s -> s.getLong(1));
            }
            throw new PersistenceException("No result found");
        });
    }

    @Override // select ${e#fields} from ${e#table} order by name
    public List<SimpleFlatEntity> findAll() {
        return database.query(
                "select id, name, SIMPLE_AGE from SIMPLE_FLAT_ENTITY order by name", NONE,
                r -> {
                    ensureInitListMapper(r);
                    return r.mapAll(entityListMapper);
                });
    }

    @Override
    public SimpleFlatEntity findOne(final String name) {
        return database.query(
                "select id, name, SIMPLE_AGE from SIMPLE_FLAT_ENTITY where name = ?",
                b -> b.bind(name),
                r -> mapFirst(r, it -> database.mapOne(SimpleFlatEntity.class, it)));
    }

    @Override
    public SimpleFlatEntity findOneWithPlaceholders(final String name) {
        return database.query(
                "select id, name, SIMPLE_AGE from SIMPLE_FLAT_ENTITY where name = ?",
                b -> b.bind(name),
                r -> mapFirst(r, it -> database.mapOne(SimpleFlatEntity.class, it)));
    }

    @Override
    public List<SimpleFlatEntity> findByName(final List<String> name) {
        return database.query(
                "select id, name, SIMPLE_AGE from SIMPLE_FLAT_ENTITY where " +
                        (name == null || name.isEmpty() ?
                                "1 <> 1" :
                                name.stream().map(it -> "?").collect(joining(",", "name in (", ")"))),
                b -> {
                    if (name != null) {
                        name.forEach(b::bind);
                    }
                },
                r -> {
                    ensureInitListMapper(r);
                    return r.mapAll(entityListMapper);
                });
    }

    @Override
    public int delete(final String name) {
        return database.execute(
                "delete from SIMPLE_FLAT_ENTITY where name like ?",
                b -> b.bind(name));
    }

    @Override
    public void deleteWithoutReturnedValue(final String name) {
        database.execute(
                "delete from SIMPLE_FLAT_ENTITY where name like ?",
                b -> b.bind(name));
    }

    private void ensureInitListMapper(final ResultSetWrapper r) {
        if (entityListMapper == null) {
            synchronized (this) {
                if (entityListMapper == null) {
                    entityListMapper = database.getOrCreateEntity(SimpleFlatEntity.class).mapper(r.get())::apply;
                }
            }
        }
    }

    // to move in an BaseOperation class with the database reference
    private <T> T mapFirst(final ResultSetWrapper rset, final SQLFunction<ResultSet, T> mapper) {
        if (!rset.hasNext()) {
            throw new PersistenceException("No result found");
        }
        try {
            final var res = mapper.apply(rset.get());
            if (rset.hasNext()) {
                throw new PersistenceException("Ambiguous entity fetched!");
            }
            return res;
        } catch (final SQLException sqlex) {
            throw new PersistenceException(sqlex);
        }
    }
}
