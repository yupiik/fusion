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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUnknownFieldsGeneratorTest {
    @Test
    void repeatedIgnoredWireNamesDoNotBreakCodecInitialization(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonIgnoredDuplicateNames").processorArgs("-Afusion.generateJsonSchemas=false")
                .compileAndJsonAsserts((loader, mapper) -> {
                    final var type = loader.apply("test.p.JsonIgnoredDuplicateNames");
                    try (final var strict = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectUnknownFields(true).build()) {
                        for (final var mode : List.of(mapper, strict)) {
                            assertEquals("{\"visible\":\"after\"}", mode.toString(mode.fromString(type,
                                    "{\"hidden\":{\"skipped\":[]},\"visible\":\"after\"}")));
                        }
                    }
                });
    }

    @Test
    void rejectionIsOptInAndUsesWireNames(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonUnknownFields").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonUnknownFields$Model");
            try (final var strict = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectUnknownFields(true).build()) {
                for (final var value : List.of("null", "true", "42", "\"text\"", "{\"nested\":[{}]}", "[{},null]")) {
                    final var json = "{\"unknown\":" + value + ",\"wireName\":\"after\"}";
                    assertEquals("{\"wireName\":\"after\"}", mapper.toString(mapper.fromString(type, json)));
                    rejects(strict, type, json, "unknown");
                }
                assertEquals("{\"wireName\":\"known\"}", strict.toString(strict.fromString(type, "{\"wireName\":\"known\"}")));
                rejects(strict, type, "{\"name\":\"java name is not the wire name\"}", "name");
                rejects(strict, type, "{\"hidden\":1}", "hidden");
                rejects(strict, loader.apply("test.p.JsonUnknownFields$Empty"), "{\"unknown\":null}", "unknown");
                assertEquals("{}", strict.toString(strict.fromString(loader.apply("test.p.JsonUnknownFields$Empty"), "{}")));
            }
        });
    }

    @Test
    void declaredIgnoredNamesAreSkippedRatherThanRejectedOrCaptured(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonUnknownFields").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonUnknownFields$Model");
            final var capturing = loader.apply("test.p.JsonUnknownFields$Capturing");
            try (final var strict = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectUnknownFields(true).build()) {
                for (final var value : List.of("null", "true", "42", "\"text\"", "{\"nested\":[{}]}", "[{},null]")) {
                    final var ignored = "\"hidden-wire\":" + value;
                    assertEquals("{\"wireName\":\"after\"}", strict.toString(strict.fromString(type,
                            "{" + ignored + ",\"wireName\":\"after\"}")));
                    for (final var configured : List.of(mapper, strict)) {
                        final var captured = configured.fromString(capturing,
                                "{" + ignored + ",\"extra\":{\"open\":[true]},\"name\":\"after\"}");
                        assertEquals(Map.of("extra", Map.of("open", List.of(true)), "name", "after"),
                                configured.fromString(Object.class, configured.toString(captured)));
                    }
                }
            }
        });
    }

    @Test
    void rejectionPropagatesIntoModelsButNotDynamicObjects(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonUnknownFields").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonUnknownFields$Model");
            try (final var strict = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectUnknownFields(true).build();
                 final var compatibleValues = mapper.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectIncompatibleValues(false).rejectUnknownFields(true).build()) {
                for (final var json : List.of("{\"child\":{\"extra\":null}}",
                        "{\"children\":[{\"extra\":null}]}", "{\"models\":{\"any-key\":{\"extra\":null}}}")) {
                    rejects(strict, type, json, "extra");
                    rejects(compatibleValues, type, json, "extra");
                }
                final var json = "{\"dynamic\":{\"open\":{\"any\":true}},\"values\":{\"arbitrary\":[{\"any\":false}]}}";
                assertEquals(mapper.fromString(Object.class, json),
                        strict.fromString(Object.class, strict.toString(strict.fromString(type, json))));
                assertEquals(Map.of("any", Map.of("key", true)), strict.fromString(Object.class, "{\"any\":{\"key\":true}}"));
                assertEquals(Map.of("any", Map.of("key", true)), strict.fromString(Map.class, "{\"any\":{\"key\":true}}"));
                final var valid = "{\"child\":{\"value\":\"known\"}}";
                assertEquals(valid, strict.toString(strict.fromString(type, valid)));
            }
        });
    }

    private void rejects(final JsonMapper mapper, final Class<?> type, final String json, final String member) {
        final var error = assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
        assertTrue(error.getMessage().contains(member), error.getMessage());
    }
}
