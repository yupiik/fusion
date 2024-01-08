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
package io.yupiik.fusion.persistence.impl;

import io.yupiik.fusion.persistence.api.Entity;

import java.lang.reflect.Type;
import java.util.Objects;

public class ColumnMetadataImpl implements Entity.ColumnMetadata {
    private final String javaName;
    private final Type type;
    private final String columnName;
    private final boolean autoIncremented;
    private final int idIndex;

    private final int hash;

    public ColumnMetadataImpl(final String javaName, final Type type, final String columnName) {
        this(javaName, type, columnName, -1, false);
    }

    public ColumnMetadataImpl(final String javaName, final Type type, final String columnName,
                              final int idIndex, final boolean autoIncremented) {
        this.javaName = javaName;
        this.type = type;
        this.columnName = columnName;
        this.idIndex = idIndex;
        this.autoIncremented = autoIncremented;

        this.hash = Objects.hash(javaName, type, columnName);
    }

    @Override
    public int idIndex() {
        return idIndex;
    }

    @Override
    public boolean autoIncremented() {
        return autoIncremented;
    }

    @Override
    public String javaName() {
        return javaName;
    }

    @Override
    public String columnName() {
        return columnName;
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public String toAliasName(final String alias) {
        return alias.isEmpty() ?
                javaName() :
                (alias + Character.toUpperCase(javaName.charAt(0)) + (javaName.length() == 1 ? "" : javaName.substring(1)));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ColumnMetadataImpl that &&
                javaName.equals(that.javaName) && type.equals(that.type) && columnName.equals(that.columnName);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
