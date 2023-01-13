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

import io.yupiik.fusion.persistence.api.Entity;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class ColumnMetadataImpl implements Entity.ColumnMetadata {
    private final Annotation[] annotations;
    private final String javaName;
    private final Type type;
    private final String columnName;

    private final int hash;

    public ColumnMetadataImpl(final Annotation[] annotations, final String javaName, final Type type, final String columnName) {
        this.annotations = annotations;
        this.javaName = javaName;
        this.type = type;
        this.columnName = columnName;

        this.hash = Objects.hash(annotations, javaName, type, columnName);
    }

    @Override
    public Annotation[] getAnnotations() {
        return annotations;
    }

    @Override
    public <T extends Annotation> T getAnnotation(final Class<T> type) {
        return Stream.of(annotations).filter(it -> it.annotationType() == type).map(type::cast).findFirst().orElse(null);
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ColumnMetadataImpl that = ColumnMetadataImpl.class.cast(o);
        return Arrays.equals(annotations, that.annotations) && javaName.equals(that.javaName) && type.equals(that.type) && columnName.equals(that.columnName);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
