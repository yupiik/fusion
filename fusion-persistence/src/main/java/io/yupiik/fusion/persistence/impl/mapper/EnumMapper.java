/*
 * Copyright (c) 2021-2022 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.persistence.impl.mapper;

import io.yupiik.fusion.framework.build.api.persistence.Column;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class EnumMapper<E extends Enum<?>> implements Column.ValueMapper<String, E> {
    private final Map<String, E> index;
    private final Map<E, String> reversedIndex;

    public EnumMapper(final Class<E> enumType) {
        this.index = Stream.of(enumType.getEnumConstants()).collect(toMap(it -> it.name(), identity()));
        this.reversedIndex = this.index.entrySet().stream().collect(toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    @Override
    public String toDatabase(final E javaValue) {
        return check(() -> javaValue == null, reversedIndex.get(javaValue), javaValue);
    }

    @Override
    public E toJava(final String databaseValue) {
        return check(() -> databaseValue == null || databaseValue.isBlank(), index.get(databaseValue), databaseValue);
    }

    private <T> T check(final BooleanSupplier testOkToHaveNull, final T value, final Object incoming) {
        if (value == null && !testOkToHaveNull.getAsBoolean()) {
            throw new IllegalArgumentException("No mapping for '" + incoming + "'");
        }
        return value;
    }
}
