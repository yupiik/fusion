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
package io.yupiik.fusion.persistence.impl.translation;

import io.yupiik.fusion.persistence.spi.DatabaseTranslation;

import java.lang.annotation.Annotation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

public class DefaultTranslation implements DatabaseTranslation {
    @Override
    public String toDatabaseType(final Class<?> type, final Annotation... annotations) {
        if (String.class == type || type.isEnum()) {
            return "VARCHAR(255)"; // todo: @Column(length) in annotations? + lob = true for "TEXT"?
        }
        if (byte.class == type) {
            return "TINYINT";
        }
        if (byte[].class == type) {
            return "VARBINARY";
        }
        if (Integer.class == type || int.class == type) {
            return "INTEGER";
        }
        if (Double.class == type || double.class == type) {
            return "DOUBLE";
        }
        if (Float.class == type || float.class == type) {
            return "FLOAT";
        }
        if (Long.class == type || long.class == type) {
            return "INTEGER";
        }
        if (Boolean.class == type || boolean.class == type) {
            return "BOOLEAN";
        }
        if (Date.class == type || LocalDate.class == type) {
            return "DATE";
        }
        if (LocalDateTime.class == type) {
            return "TIMESTAMP";
        }
        if (LocalTime.class == type) {
            return "TIME";
        }
        if (OffsetDateTime.class == type || ZonedDateTime.class == type) {
            return "TIMESTAMP WITH TIME ZONE";
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    @Override
    public String toCreateTablePrimaryKeySuffix(final List<Map.Entry<String, Annotation[]>> columns) {
        return ", PRIMARY KEY (" +
                columns.stream()
                        .map(Map.Entry::getKey)
                        .map(this::wrapFieldName)
                        .collect(joining(", ")) +
                ")";
    }
}
