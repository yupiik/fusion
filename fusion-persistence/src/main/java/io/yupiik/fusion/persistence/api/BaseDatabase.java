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
package io.yupiik.fusion.persistence.api;

import java.sql.ResultSet;
import java.util.List;

/**
 * Connection idependent operations (mainly around the mapping and model).
 */
public interface BaseDatabase {
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
