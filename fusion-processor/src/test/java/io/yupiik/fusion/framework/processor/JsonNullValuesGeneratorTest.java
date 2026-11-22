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
package io.yupiik.fusion.framework.processor;

import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.processor.test.Compiler;
import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonNullValuesGeneratorTest {
    @Test
    void nullableModelsAndContainers(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonNullableValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonNullableValues");
            final var nestedType = loader.apply("test.p.JsonNullableValues$Nested");
            try (final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
                for (final var mode : List.of(mapper, legacy)) {
                    for (final var root : List.<Type>of(type, nestedType,
                            new Types.ParameterizedTypeImpl(List.class, type),
                            new Types.ParameterizedTypeImpl(Set.class, type),
                            new Types.ParameterizedTypeImpl(Map.class, String.class, type),
                            new Types.ParameterizedTypeImpl(Map.class, String.class, new Types.ParameterizedTypeImpl(List.class, type)))) {
                        for (int repeat = 0; repeat < 3; repeat++) {
                            assertNull(mode.fromString(root, "null"));
                            assertNull(mode.read(root, new StringReader(" null ")));
                            for (final var empty : List.of("", " \n\t")) {
                                assertThrows(JsonException.class, () -> mode.fromString(root, empty));
                            }
                        }
                    }
                    final var missing = mode.fromString(type, "{}");
                    final Map<String, String> values = Map.ofEntries(
                            Map.entry("bool", "true"), Map.entry("integer", "42"), Map.entry("lng", "42"),
                            Map.entry("dbl", "42.5"), Map.entry("number", "42.5"), Map.entry("decimal", "42.5"),
                            Map.entry("text", "\"escaped \\\"text\\\" with a tail\""), Map.entry("nested", "{\"id\":\"001\"}"),
                            Map.entry("choice", "\"YES\""), Map.entry("date", "\"2026-11-17\""),
                            Map.entry("datetime", "\"2026-11-17T12:30:00\""), Map.entry("offset", "\"2026-11-17T12:30:00Z\""),
                            Map.entry("zoned", "\"2026-11-17T12:30:00Z\""));
                    values.forEach((name, value) -> {
                        for (final var suffix : List.of("", "List", "Set", "Map", "MapList")) {
                            final var field = name + suffix;
                            assertNull(field(missing, field), field);
                            assertNull(field(mode.fromString(type, "{\"" + field + "\":null}"), field), field);
                        }
                        final var json = "{\"" + name + "\":" + value +
                                ",\"" + name + "List\":[null," + value + ",null],\"" + name + "Set\":[null," + value + ",null]," +
                                "\"" + name + "Map\":{\"nil\":null,\"value\":" + value + "}," +
                                "\"" + name + "MapList\":{\"nil\":null,\"items\":[null," + value + ",null]}}";
                        final var result = mode.fromString(type, json);
                        final var expected = field(result, name);
                        final var list = (List<?>) field(result, name + "List");
                        assertEquals(3, list.size(), name);
                        assertNull(list.get(0), name);
                        assertEquals(expected, list.get(1), name);
                        assertNull(list.get(2), name);
                        final var set = (Collection<?>) field(result, name + "Set");
                        assertEquals(2, set.size(), name);
                        assertTrue(set.contains(null), name);
                        assertTrue(set.contains(expected), name);
                        final var map = (Map<?, ?>) field(result, name + "Map");
                        assertTrue(map.containsKey("nil"), name);
                        assertNull(map.get("nil"), name);
                        assertEquals(expected, map.get("value"), name);
                        final var mapList = (Map<?, ?>) field(result, name + "MapList");
                        assertTrue(mapList.containsKey("nil"), name);
                        assertNull(mapList.get("nil"), name);
                        assertEquals(list, mapList.get("items"), name);
                    });
                    // Both orders check that optional nested objects and strings leave the cursor at the next field.
                    for (final var json : List.of("{\"nested\":null,\"text\":\"after\"}", "{\"text\":\"after\",\"nested\":null}", "{\"text\":\"after\"}")) {
                        final var result = mode.fromString(type, json);
                        assertNull(field(result, "nested"));
                        assertEquals("after", field(result, "text"));
                    }
                    for (final var json : List.of("{\"text\":null,\"nested\":{\"id\":\"001\"}}", "{\"nested\":{\"id\":\"001\"},\"text\":null}", "{\"nested\":{\"id\":\"001\"}}")) {
                        final var result = mode.read(type, new StringReader(json));
                        assertNull(field(result, "text"));
                        assertEquals("001", field(field(result, "nested"), "id"));
                    }
                }
            }
        });
    }

    private Object field(final Object model, final String name) {
        try {
            return model.getClass().getMethod(name).invoke(model);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
