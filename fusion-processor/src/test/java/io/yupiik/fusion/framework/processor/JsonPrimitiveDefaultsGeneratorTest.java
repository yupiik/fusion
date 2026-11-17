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

import io.yupiik.fusion.framework.processor.test.Compiler;
import io.yupiik.fusion.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonPrimitiveDefaultsGeneratorTest {
    @Test
    void nullResetsPrimitivesButNotWrappers(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonPrimitiveDefaults").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonPrimitiveDefaults");
            try (final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
                for (final var current : List.of(mapper, legacy)) {
                    for (final var json : List.of("{}",
                            "{\"flag\":null,\"count\":null,\"total\":null,\"ratio\":null,\"optionalFlag\":null,\"optionalCount\":null,\"optionalTotal\":null,\"optionalRatio\":null}",
                            "{\"flag\":true,\"flag\":null,\"count\":42,\"count\":null,\"total\":43,\"total\":null,\"ratio\":1.5,\"ratio\":null,\"optionalFlag\":true,\"optionalFlag\":null,\"optionalCount\":42,\"optionalCount\":null,\"optionalTotal\":43,\"optionalTotal\":null,\"optionalRatio\":1.5,\"optionalRatio\":null}")) {
                        final var model = current.fromString(type, json);
                        Map.of("flag", false, "count", 0, "total", 0L, "ratio", 0d)
                                .forEach((name, expected) -> assertEquals(expected, field(model, name), name));
                        for (final var name : List.of("optionalFlag", "optionalCount", "optionalTotal", "optionalRatio")) {
                            assertNull(field(model, name), name);
                        }
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
