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
package io.yupiik.fusion.persistence.impl.query;

import io.yupiik.fusion.persistence.impl.DatabaseImpl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QueryCompiler {
    private final DatabaseImpl database;
    private final Map<QueryKey<?>, CompiledQuery> queries = new ConcurrentHashMap<>();

    public QueryCompiler(final DatabaseImpl database) {
        this.database = database;
    }

    @SuppressWarnings("unchecked")
    public <T> CompiledQuery<T> getOrCreate(final QueryKey<T> key) {
        return queries.computeIfAbsent(key, this::compute);
    }

    // todo: named parameters support?
    private <T> CompiledQuery<T> compute(final QueryKey<T> queryKey) {
        return new CompiledQuery<>(database, queryKey);
    }
}
