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

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonPrimitiveDefaultsTest {
    @Test
    void primitiveRootsDefaultWhileWrappersRemainNull() {
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = strict.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
            for (final var mapper : List.of(strict, legacy)) {
                Map.of(boolean.class, false, int.class, 0, long.class, 0L, float.class, 0f, double.class, 0d)
                        .forEach((type, expected) -> {
                            assertEquals(expected, mapper.fromString(type, "null"));
                            assertEquals(expected, mapper.read(type, new StringReader("null")));
                            assertEquals(expected, mapper.read((Type) type, new StringReader("null")));
                        });
                for (final var type : List.of(Boolean.class, Integer.class, Long.class, Float.class, Double.class)) {
                    assertNull(mapper.fromString(type, "null"));
                    assertNull(mapper.read(type, new StringReader("null")));
                    assertNull(mapper.read((Type) type, new StringReader("null")));
                }
            }
        }
    }
}
