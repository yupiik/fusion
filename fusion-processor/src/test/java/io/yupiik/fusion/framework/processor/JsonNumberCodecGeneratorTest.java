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
import io.yupiik.fusion.json.JsonException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonNumberCodecGeneratorTest {
    @Test
    void incompatibleFieldShapesFailBeforeConstructorCasts(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonRecords").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonRecords$AllInOne");
            final var tokens = List.of("true", "false", "42", "\"text\"", "{}", "[]");
            final var accepted = Map.of(
                    "aBool", List.of("true", "false"),
                    "integer", List.of("42"), "nullableInt", List.of("42"),
                    "lg", List.of("42"), "more", List.of("42"),
                    "simplest", List.of("\"text\""), "nested", List.of("{}"),
                    "bigNumber", List.of("42", "\"text\""));
            accepted.forEach((name, valid) -> {
                for (final var token : tokens) {
                    if (!valid.contains(token)) {
                        final var json = "{\"" + name + "\":" + token + "}";
                        assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
                    }
                }
            });
            for (final var name : List.of("booleanList", "bigNumbers", "intList", "longList", "doubleList",
                    "stringList", "dateList", "dateTimeList", "offsetList", "zonedList", "genericList", "nestedList")) {
                for (final var token : tokens) {
                    if (!"[]".equals(token)) {
                        final var json = "{\"" + name + "\":" + token + "}";
                        assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
                    }
                }
            }
            for (final var name : List.of("mapStringString", "mapStringInt", "mapNested")) {
                for (final var token : tokens) {
                    if (!"{}".equals(token)) {
                        final var json = "{\"" + name + "\":" + token + "}";
                        assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
                    }
                }
            }
            for (final var name : List.of("date", "dateTime", "offset", "zoned")) {
                for (final var token : List.of("42", "true", "false", "{}", "[]")) {
                    final var json = "{\"" + name + "\":" + token + "}";
                    assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
                }
            }
        });
    }

    @Test
    void numberFieldsAndContainers(@TempDir final Path work) throws IOException {
        final var compiler = new Compiler(work, "JsonNumberModel");
        compiler.compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonNumberModel");
            final var json = "{\"decimal\":\"12.50\",\"mappedNumbers\":{\"integer\":42,\"fraction\":1.25},\"number\":12345678901234567890.125,\"numbers\":[1,-2.5,3e2]}";
            final var model = mapper.fromString(type, json);
            assertEquals(new BigDecimal("12345678901234567890.125"), field(model, "number"));
            assertEquals(List.of(new BigDecimal("1"), new BigDecimal("-2.5"), new BigDecimal("3e2")), field(model, "numbers"));
            assertEquals(Map.of("integer", new BigDecimal("42"), "fraction", new BigDecimal("1.25")), field(model, "mappedNumbers"));
            assertEquals(new BigDecimal("12.50"), field(model, "decimal"));
            // Number's BigDecimal implementation stays numeric; a declared BigDecimal stays quoted.
            assertEquals(mapper.fromString(Object.class, json), mapper.fromString(Object.class, mapper.toString(model)));
            assertTrue(mapper.toString(model).contains("\"number\":12345678901234567890.125"));
            assertTrue(mapper.toString(model).contains("\"decimal\":\"12.50\""));
            assertNull(field(mapper.fromString(type, "{}"), "number"));
            assertNull(field(mapper.fromString(type, "{\"number\":null}"), "number"));

            final var source = compiler.readGeneratedSources();
            assertTrue(source.contains("ValueKind.NUMBER"));
            assertTrue(source.contains("java.lang.Number.class"));
            try {
                final var heterogeneous = type.getConstructor(Number.class, List.class, Map.class, BigDecimal.class)
                        .newInstance(7L, List.of(1, 2L, 3.5, new BigInteger("12345678901234567890"), new BigDecimal("4.25")),
                                Map.of("decimal", new BigDecimal("5.25")), null);
                assertEquals(mapper.fromString(Object.class,
                                "{\"mappedNumbers\":{\"decimal\":5.25},\"number\":7,\"numbers\":[1,2,3.5,12345678901234567890,4.25]}"),
                        mapper.fromString(Object.class, mapper.toString(heterogeneous)));
                final var schemas = mapper.fromString(Map.class, Files.readString(compiler.getClasses().resolve("META-INF/fusion/json/schemas.json")));
                final var schema = (Map<?, ?>) ((Map<?, ?>) schemas.get("schemas")).get("test.p.JsonNumberModel");
                final var properties = (Map<?, ?>) schema.get("properties");
                final var numberSchema = Map.of("type", "number", "nullable", true);
                assertEquals(numberSchema, properties.get("number"));
                assertEquals(numberSchema, ((Map<?, ?>) properties.get("numbers")).get("items"));
                assertEquals(numberSchema, ((Map<?, ?>) properties.get("mappedNumbers")).get("additionalProperties"));
            } catch (final IOException | ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void bigDecimalAcceptsStringsAndNumbers(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonNumberModel").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonNumberModel");
            for (final var value : List.of("12.50", "\"12.50\"")) {
                final var model = mapper.fromString(type, "{\"decimal\":" + value + "}");
                assertEquals(new BigDecimal("12.50"), field(model, "decimal"));
                assertEquals("{\"decimal\":\"12.50\"}", mapper.toString(model));
            }
        });
    }

    @Test
    void genericObjectsAcceptEveryShapeAndMixedStringIds(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonObjectShapes").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonObjectShapes");
            final var shapes = List.of("null", "true", "false", "1.25", "\"00123\"", "[]", "{}",
                    "[\"00123\",42,true,null,{\"id\":\"0007\"},[false,2]]",
                    "{\"id\":\"00123\",\"count\":42,\"active\":true,\"nested\":[null,{\"id\":\"0007\"}]}");
            for (final var shape : shapes) {
                final var expected = mapper.fromString(Object.class, shape);
                final var model = mapper.fromString(type, "{\"value\":" + shape + ",\"values\":[" + shape + "],\"mappedValues\":{\"id\":\"0001\",\"payload\":" + shape + "}}");
                assertEquals(expected, field(model, "value"), shape);
                assertEquals(Arrays.asList(expected), field(model, "values"), shape);
                final var mapped = (Map<?, ?>) field(model, "mappedValues");
                assertEquals("0001", mapped.get("id"));
                assertTrue(mapped.containsKey("payload"));
                assertEquals(expected, mapped.get("payload"), shape);
                final var reread = mapper.fromString(type, mapper.toString(model));
                assertEquals(field(model, "value"), field(reread, "value"));
                assertEquals(field(model, "values"), field(reread, "values"));
            }
        });
    }

    @Test
    void incompatibleScalarsAreStrictUnlessLegacyIsRequested(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonScalarCompatibility").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonScalarCompatibility");
            try (final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
                for (final var scalar : List.of("true", "false", "42", "\"not a record\"")) {
                    final var json = "{\"nested\":" + scalar + ",\"text\":\"after\"}";
                    assertThrows(IllegalStateException.class, () -> mapper.fromString(type, json), json);
                    final var model = legacy.fromString(type, json);
                    assertNull(field(model, "nested"));
                    assertEquals("after", field(model, "text"));
                }
                for (final var scalar : List.of("true", "false", "42")) {
                    final var json = "{\"text\":" + scalar + ",\"nested\":{\"id\":\"00123\"}}";
                    assertThrows(IllegalStateException.class, () -> mapper.fromString(type, json), json);
                    assertNull(field(legacy.fromString(type, json), "text"));
                    assertEquals("00123", field(field(legacy.fromString(type, json), "nested"), "id"));
                }
                final var missing = mapper.fromString(type, "{\"text\":\"optional\"}");
                assertNull(field(missing, "nested"));
                assertNull(field(mapper.fromString(type, "{\"nested\":null}"), "nested"));
            }
        });
    }

    @Test
    void rejectedStringsLeaveLegacyCursorAtNextField(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonNumberModel").compileAndJsonAsserts((loader, mapper) -> {
            final var type = loader.apply("test.p.JsonNumberModel");
            final var json = "{\"number\":\"not a number with an escaped \\\"quote\\\" and a long tail\",\"decimal\":12.50}";
            assertThrows(IllegalStateException.class, () -> mapper.fromString(type, json));
            assertThrows(IllegalStateException.class, () -> mapper.read(type, new StringReader(json)));
            try (final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
                assertThrows(IllegalStateException.class, () -> legacy.fromString(type, json));
                assertThrows(IllegalStateException.class, () -> legacy.read(type, new StringReader(json)));
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
