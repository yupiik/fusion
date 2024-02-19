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
package io.yupiik.fusion.persistence.api;

import java.sql.ResultSet;
import java.util.List;
import java.util.function.Function;

/**
 * Connection independent operations (mainly around the mapping and model).
 */
public interface BaseDatabase {
    /**
     * Enable to be used for "count" queries and extract more easily the first result as a long.
     */
    Function<ResultSetWrapper, Long> FIRST_LONG = r -> r.hasNext() ? r.map(it -> it.getLong(1)) : 0L;

    <T, ID> Entity<T, ID> entity(Class<T> type);

    /**
     * @param type      entity type.
     * @param resultSet resultset positionned at the row to map (next() already called).
     * @param <T>       entity type.
     * @return the mapped entity.
     */
    <T> T mapOne(Class<T> type, ResultSet resultSet);

    <T> List<T> mapAll(Class<T> type, ResultSet resultSet);
}
