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

import java.util.Objects;

public class QueryKey<T> {
    private final Class<T> type;
    private final String sql;
    private final int hash;

    public QueryKey(final Class<T> type, final String sql) {
        this.type = type;
        this.sql = sql;
        this.hash = Objects.hash(type, sql);
    }

    public Class<T> getType() {
        return type;
    }

    public String getSql() {
        return sql;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryKey<?> queryKey = (QueryKey<?>) o;
        return sql.equals(queryKey.sql) && type.equals(queryKey.type);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
