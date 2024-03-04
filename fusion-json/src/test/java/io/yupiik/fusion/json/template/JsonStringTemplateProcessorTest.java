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
package io.yupiik.fusion.json.template;

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonStringTemplateProcessorTest {
    @Test
    void interpolate() {
        try (final var mapper =new JsonMapperImpl(List.of(), c -> empty())) {
            final var processor = new JsonStringTemplateProcessor<>(mapper, Object.class);

            {
                final var name = "test";
                assertEquals(Map.of("name", "test"), processor."""
                    {
                      "name": "\{name}"
                    }""");
            }

            {
                final var name = "test \"with quotes\" :)";
                final int age = 33;
                assertEquals(Map.of("name", "test \"with quotes\" :)", "age", BigDecimal.valueOf(33)), processor."""
                    {
                      "name": "\{name}",
                      "age": \{age}
                    }""");
            }
        }
    }
}
