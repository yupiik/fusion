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
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTypedValuesGeneratorTest {
    @Test
    void duplicatesAreCheckedInModeledCapturedIgnoredAndSkippedMembers(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues", "JsonRecords", "JsonIgnoreModel").compileAndJsonAsserts((loader, mapper) -> {
            try (final var strict = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectDuplicateKeys(true).build()) {
                final var modeled = loader.apply("test.p.JsonTypedValues");
                for (final var json : List.of("{\"text\":null,\"text\":\"answer\"}",
                        "{\"text\":\"first\",\"\\u0074ext\":\"answer\"}",
                        "{\"unknown\":1,\"unknown\":2}",
                        "{\"unknown\":[{\"x\":1,\"x\":2}]}",
                        "{\"nested\":{\"id\":\"1\",\"id\":\"2\"}}",
                        "{\"objectMap\":{\"x\":null,\"x\":1}}")) {
                    assertThrows(JsonException.class, () -> strict.fromString(modeled, json), json);
                }
                final var ignored = loader.apply("test.p.JsonIgnoreModel$AllObjects");
                assertThrows(JsonException.class, () -> strict.fromString(ignored,
                        "{\"hiddenString\":null,\"hiddenString\":\"ignored\"}"));
                assertThrows(JsonException.class, () -> strict.fromString(ignored,
                        "{\"hiddenObject\":{\"x\":1,\"x\":2}}"));
                final var captured = loader.apply("test.p.JsonRecords$OthersOnly");
                assertThrows(JsonException.class, () -> strict.fromString(captured, "{\"x\":null,\"x\":2}"));
                assertEquals("answer", field(mapper.fromString(modeled,
                        "{\"text\":\"first\",\"text\":\"answer\"}"), "text"));
                final var valid = "{\"models\":[{\"id\":\"1\"},{\"id\":\"2\"}],\"text\":\"after\"}";
                assertEquals("after", field(strict.fromString(modeled, valid), "text"));
            }
        });
    }

    @Test
    void rejectsIncompatibleScalarFields(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonTypedValues");
            for (final var name : List.of("primitiveBoolean", "boxedBoolean")) {
                rejects(mapper, type, name, "42", "\"true\"", "[]", "{}");
            }
            for (final var name : List.of("primitiveInt", "primitiveLong", "primitiveDouble", "boxedInt", "boxedLong", "boxedDouble")) {
                rejects(mapper, type, name, "true", "false", "\"42\"", "[]", "{}");
            }
            for (final var name : List.of("text", "date", "dateTime", "offset", "zoned", "choice")) {
                rejects(mapper, type, name, "true", "false", "42", "[]", "{}");
            }
            rejects(mapper, type, "nested", "true", "false", "42", "\"not a model\"", "[]");
            rejects(mapper, type, "decimal", "true", "false", "[]", "{}");
        });
    }

    @Test
    void rejectsScalarsAndWrongContainerShapes(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonTypedValues");
            for (final var name : List.of("integers", "longs", "doubles", "booleans", "strings", "decimals", "dates", "choices",
                    "models", "objects", "integerSet", "objectSet")) {
                rejects(mapper, type, name, "42", "\"value\"", "true", "false", "{}", "{\"key\":1}");
            }
            for (final var name : List.of("integerMap", "stringMap", "booleanMap", "modelMap", "objectMap",
                    "integerLists", "modelLists", "objectLists")) {
                rejects(mapper, type, name, "42", "\"value\"", "true", "false", "[]", "[{}]");
            }
        });
    }

    @Test
    void rejectsWrongElementsAndMapValues(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonTypedValues");
            for (final var name : List.of("integers", "longs", "doubles", "integerSet")) {
                rejects(mapper, type, name, "[1,true]", "[1,\"2\"]", "[1,{}]", "[1,[]]");
            }
            rejects(mapper, type, "booleans", "[true,42]", "[true,\"false\"]", "[true,{}]", "[true,[]]");
            for (final var name : List.of("strings", "dates", "choices")) {
                rejects(mapper, type, name, "[42]", "[true]", "[{}]", "[[]]");
            }
            rejects(mapper, type, "models", "[42]", "[true]", "[\"value\"]", "[[]]");
            rejects(mapper, type, "decimals", "[true]", "[{}]", "[[]]");
            rejects(mapper, type, "integerMap", "{\"key\":true}", "{\"key\":\"42\"}", "{\"key\":{}}", "{\"key\":[]}");
            rejects(mapper, type, "stringMap", "{\"key\":42}", "{\"key\":true}", "{\"key\":{}}", "{\"key\":[]}");
            rejects(mapper, type, "booleanMap", "{\"key\":42}", "{\"key\":\"true\"}", "{\"key\":{}}", "{\"key\":[]}");
            rejects(mapper, type, "modelMap", "{\"key\":42}", "{\"key\":true}", "{\"key\":\"value\"}", "{\"key\":[]}");
            for (final var name : List.of("integerLists", "modelLists", "objectLists")) {
                rejects(mapper, type, name, "{\"key\":42}", "{\"key\":true}", "{\"key\":\"value\"}", "{\"key\":{}}");
            }
            rejects(mapper, type, "integerLists", "{\"key\":[1,true]}", "{\"key\":[\"42\"]}", "{\"key\":[{}]}", "{\"key\":[[]]}");
            rejects(mapper, type, "modelLists", "{\"key\":[42]}", "{\"key\":[true]}", "{\"key\":[\"value\"]}", "{\"key\":[[]]}");
        });
    }

    @Test
    void acceptsTypedScalarsAndContainers(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonTypedValues");
            final var model = mapper.fromString(type, """
                    {"primitiveBoolean":true,"boxedBoolean":false,"primitiveInt":12,"boxedInt":34,
                     "primitiveLong":2147483648,"boxedLong":2147483649,"primitiveDouble":1.25,"boxedDouble":2.5,
                     "text":"00123","date":"2026-11-17","dateTime":"2026-11-17T10:15:30",
                     "offset":"2026-11-17T10:15:30Z","zoned":"2026-11-17T10:15:30Z","choice":"FIRST",
                     "nested":{"id":"0007"},"integers":[1,2],"longs":[2147483648],"doubles":[1.25],
                     "booleans":[true,false],"strings":["00123"],"dates":["2026-11-17"],"choices":["SECOND"],
                     "models":[{"id":"0007"}],"integerSet":[1,1,2],"integerMap":{"key":42},
                     "stringMap":{"key":"00123"},"booleanMap":{"key":true},"modelMap":{"key":{"id":"0007"}},
                     "integerLists":{"key":[1,2]},"modelLists":{"key":[{"id":"0007"}]}}
                    """);
            for (final var entry : Map.<String, Object>ofEntries(
                    Map.entry("primitiveBoolean", true), Map.entry("boxedBoolean", false),
                    Map.entry("primitiveInt", 12), Map.entry("boxedInt", 34),
                    Map.entry("primitiveLong", 2147483648L), Map.entry("boxedLong", 2147483649L),
                    Map.entry("primitiveDouble", 1.25), Map.entry("boxedDouble", 2.5),
                    Map.entry("text", "00123"), Map.entry("date", LocalDate.parse("2026-11-17")),
                    Map.entry("dateTime", LocalDateTime.parse("2026-11-17T10:15:30")),
                    Map.entry("offset", OffsetDateTime.parse("2026-11-17T10:15:30Z")),
                    Map.entry("zoned", ZonedDateTime.parse("2026-11-17T10:15:30Z")),
                    Map.entry("integers", List.of(1, 2)), Map.entry("longs", List.of(2147483648L)),
                    Map.entry("doubles", List.of(1.25)), Map.entry("booleans", List.of(true, false)),
                    Map.entry("strings", List.of("00123")), Map.entry("dates", List.of(LocalDate.parse("2026-11-17"))),
                    Map.entry("integerSet", Set.of(1, 2)), Map.entry("integerMap", Map.of("key", 42)),
                    Map.entry("stringMap", Map.of("key", "00123")), Map.entry("booleanMap", Map.of("key", true)),
                    Map.entry("integerLists", Map.of("key", List.of(1, 2)))).entrySet()) {
                assertEquals(entry.getValue(), field(model, entry.getKey()), entry.getKey());
            }
            assertEquals("FIRST", field(model, "choice").toString());
            assertEquals("SECOND", ((List<?>) field(model, "choices")).get(0).toString());
            final var nested = field(model, "nested");
            assertEquals("0007", field(nested, "id"));
            assertEquals(List.of(nested), field(model, "models"));
            assertEquals(Map.of("key", nested), field(model, "modelMap"));
            assertEquals(Map.of("key", List.of(nested)), field(model, "modelLists"));
            assertEquals(model, mapper.fromString(type, mapper.toString(model)));
        });
    }

    @Test
    void objectValuesRemainPolymorphicWithinTheirContainers(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonTypedValues");
            for (final var shape : List.of("true", "false", "42", "1.25", "\"00123\"", "[]", "{}",
                    "[1,\"00123\",true,{\"id\":\"0007\"}]", "{\"id\":\"0007\",\"items\":[1,false]}")) {
                final var expected = mapper.fromString(Object.class, shape);
                final var model = mapper.fromString(type, "{\"generic\":" + shape + ",\"objects\":[" + shape +
                        "],\"objectSet\":[" + shape + "],\"objectMap\":{\"key\":" + shape +
                        "},\"objectLists\":{\"key\":[" + shape + "]}}");
                assertEquals(expected, field(model, "generic"), shape);
                assertEquals(List.of(expected), field(model, "objects"), shape);
                assertEquals(Set.of(expected), field(model, "objectSet"), shape);
                assertEquals(Map.of("key", expected), field(model, "objectMap"), shape);
                assertEquals(Map.of("key", List.of(expected)), field(model, "objectLists"), shape);
                assertEquals(model, mapper.fromString(type, mapper.toString(model)), shape);
            }
        });
    }

    @Test
    void bigDecimalAcceptsStringsAndNumbersInBothModes(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonTypedValues");
            try (final var compatible = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
                for (final var mode : List.of(mapper, compatible)) {
                    for (final var value : List.of("12.50", "\"12.50\"")) {
                        final var model = mode.fromString(type, "{\"decimal\":" + value + ",\"decimals\":[" + value + "],\"text\":\"after\"}");
                        assertEquals(new BigDecimal("12.50"), field(model, "decimal"));
                        assertEquals(List.of(new BigDecimal("12.50")), field(model, "decimals"));
                        assertEquals("after", field(model, "text"));
                        assertEquals(model, mode.fromString(type, mode.toString(model)));
                    }
                }
            }
        });
    }

    @Test
    void compatibilityIgnoresScalarMismatchesAndContinuesAtNextField(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonTypedValues").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonTypedValues");
            try (final var compatible = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
                for (final var scalar : List.of("true", "false", "42", "\"ignored with an escaped \\\"quote\\\" and a tail\"")) {
                    final var json = "{\"nested\":" + scalar + ",\"text\":\"after\",\"integers\":[1,2]}";
                    assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
                    final var model = compatible.read(type, new StringReader(json));
                    assertNull(field(model, "nested"));
                    assertEquals("after", field(model, "text"));
                    assertEquals(List.of(1, 2), field(model, "integers"));
                }
                for (final var scalar : List.of("true", "false", "42")) {
                    final var json = "{\"text\":" + scalar + ",\"nested\":{\"id\":\"0007\"},\"boxedInt\":17}";
                    assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
                    final var model = compatible.read(type, new StringReader(json));
                    assertNull(field(model, "text"));
                    assertEquals("0007", field(field(model, "nested"), "id"));
                    assertEquals(17, field(model, "boxedInt"));
                }
            }
        });
    }

    private void rejects(final JsonMapper mapper, final Class<?> type, final String name, final String... values) {
        for (final var value : values) {
            final var json = "{\"" + name + "\":" + value + "}";
            assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
        }
    }

    private Object field(final Object model, final String name) {
        try {
            return model.getClass().getMethod(name).invoke(model);
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
