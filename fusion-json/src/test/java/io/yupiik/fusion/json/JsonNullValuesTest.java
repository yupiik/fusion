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
package io.yupiik.fusion.json;

import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.patch.JsonPatchOperation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonNullValuesTest {
    @ParameterizedTest
    @MethodSource("rootTypes")
    void nullableRootsAndEmptyDocuments(final Type type) {
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = strict.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
            for (final var mapper : List.of(strict, legacy)) {
                for (int repeat = 0; repeat < 3; repeat++) {
                    assertNull(mapper.fromString(type, "null"));
                    assertNull(mapper.read(type, new StringReader(" null ")));
                    if (type instanceof Class<?> clazz) {
                        assertNull(mapper.read(clazz, new StringReader("null")));
                    }
                    for (final var empty : List.of("", " ", "\n\t\r")) {
                        assertThrows(JsonException.class, () -> mapper.fromString(type, empty));
                        assertThrows(JsonException.class, () -> mapper.read(type, new StringReader(empty)));
                        if (type instanceof Class<?> clazz) {
                            assertThrows(JsonException.class, () -> mapper.read(clazz, new StringReader(empty)));
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("valueTypes")
    void nullableContainerEntries(final Class<?> type) {
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = strict.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
            for (final var mapper : List.of(strict, legacy)) {
                for (int repeat = 0; repeat < 3; repeat++) {
                    assertEquals(Arrays.asList(null, null), mapper.fromString(new Types.ParameterizedTypeImpl(List.class, type), "[null,null]"));
                    assertEquals(new HashSet<>(Arrays.asList((Object) null)), mapper.fromString(new Types.ParameterizedTypeImpl(Set.class, type), "[null,null]"));
                    final Map<String, ?> map = mapper.fromString(new Types.ParameterizedTypeImpl(Map.class, String.class, type), "{\"nil\":null}");
                    assertTrue(map.containsKey("nil"));
                    assertNull(map.get("nil"));
                    final Map<String, ?> mapList = mapper.fromString(new Types.ParameterizedTypeImpl(Map.class, String.class,
                            new Types.ParameterizedTypeImpl(List.class, type)), "{\"nil\":null,\"items\":[null,null]}");
                    assertTrue(mapList.containsKey("nil"));
                    assertNull(mapList.get("nil"));
                    assertEquals(Arrays.asList(null, null), mapList.get("items"));
                }
            }
        }
    }

    private static Stream<Class<?>> valueTypes() {
        return Stream.of(String.class, Boolean.class, Integer.class, Long.class, Float.class, Double.class,
                Number.class, BigDecimal.class, LocalDate.class, LocalDateTime.class, OffsetDateTime.class,
                ZonedDateTime.class, JsonPatchOperation.Operation.class, Object.class);
    }

    private static Stream<Type> rootTypes() {
        return Stream.concat(Stream.concat(valueTypes(), Stream.of(Map.class, JsonPatchOperation.class)), valueTypes().flatMap(type -> Stream.of(
                new Types.ParameterizedTypeImpl(List.class, type),
                new Types.ParameterizedTypeImpl(Collection.class, type),
                new Types.ParameterizedTypeImpl(Set.class, type),
                new Types.ParameterizedTypeImpl(Map.class, String.class, type),
                new Types.ParameterizedTypeImpl(Map.class, String.class, new Types.ParameterizedTypeImpl(List.class, type)))));
    }
}
