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
import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTypedCompatibilityTest {
    @Test
    void skippedIncompatibleContainersDoNotConsumeTheFollowingMember(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonTypedValues");
            try (final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow()
                    .rejectIncompatibleValues(false).build()) {
                for (final var json : List.of(
                        "{\"integers\":{\"nested\":[{},{}]},\"text\":\"after\"}",
                        "{\"text\":[{\"nested\":[{}]}],\"text\":\"after\"}",
                        "{\"nested\":[{\"ignored\":[true,null]}],\"text\":\"after\"}")) {
                    assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
                    final var model = legacy.fromString(type, json);
                    assertNull(field(model, "integers"));
                    assertNull(field(model, "nested"));
                    assertEquals("after", field(model, "text"));
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
