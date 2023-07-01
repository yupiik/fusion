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

import io.yupiik.fusion.persistence.api.BaseDatabase;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.api.PersistenceException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Locale.ROOT;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class DefaultBaseDatabase implements BaseDatabase {
    protected final Function<Class<?>, Object> instanceLookup;
    private final Map<Class<?>, Entity<?, ?>> entities = new ConcurrentHashMap<>();

    public DefaultBaseDatabase(final Function<Class<?>, Object> instanceLookup) {
        this.instanceLookup = instanceLookup;
    }

    public Function<Class<?>, Object> getInstanceLookup() {
        return instanceLookup;
    }

    // mainly enables some cleanup if needed, not exposed as such in the API
    public Map<Class<?>, Entity<?, ?>> getEntities() {
        return entities;
    }

    @Override
    public <T, ID> Entity<T, ID> entity(final Class<T> type) {
        return getEntityImpl(type);
    }

    @SuppressWarnings("unchecked")
    public <T> T mapOne(final Class<T> type, final ResultSet resultSet) {
        return type == Map.class ?
                (T) mapAsMap(toNames(resultSet), resultSet) :
                entity(type).mapper(resultSet).apply(resultSet);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> mapAll(final Class<T> type, final ResultSet resultSet) {
        final var provider = type == Map.class ?
                new Function<ResultSet, T>() {
                    private final List<String> names;

                    {
                        names = toNames(resultSet);
                    }

                    @Override
                    public T apply(final ResultSet line) {
                        return (T) mapAsMap(names, line);
                    }
                } :
                entity(type).mapper(resultSet);
        return new ResultSetWrapperImpl(resultSet).mapAll(provider::apply);
    }

    @SuppressWarnings("unchecked")
    private <T, ID> Entity<T, ID> getEntityImpl(final Class<T> type) {
        return (Entity<T, ID>) requireNonNull(
                entities.computeIfAbsent(type, t -> (Entity<?, ?>) getInstanceLookup().apply(type)),
                () -> "Missing entity '" + type.getName() + "', did you check you generated the related Entity model as a bean?");
    }

    protected List<String> toNames(final ResultSet resultSet) {
        final List<String> names;
        try {
            final var metaData = resultSet.getMetaData();
            names = IntStream.rangeClosed(1, metaData.getColumnCount())
                    .mapToObj(i -> {
                        try {
                            final var columnLabel = metaData.getColumnLabel(i);
                            return columnLabel == null || columnLabel.isBlank() ? metaData.getColumnName(i) : columnLabel;
                        } catch (final SQLException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    // most of databases are case insensitive and all can be made friendly so make the matching easier
                    .map(it -> it.toLowerCase(ROOT))
                    .collect(toList());
        } catch (final SQLException s) {
            throw new PersistenceException(s);
        }
        return names;
    }

    protected Map<String, ?> mapAsMap(final List<String> names, final ResultSet line) {
        return names.stream().flatMap(it -> {
            try {
                final var object = line.getObject(it);
                return object == null ? Stream.empty() : Stream.of(entry(it, object));
            } catch (final SQLException e) {
                throw new PersistenceException(e);
            }
        }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
