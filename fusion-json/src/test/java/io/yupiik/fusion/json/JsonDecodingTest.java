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
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonDecodingTest {
    @Test
    void rawMapsRequireObjectsWhileObjectAndLegacyRemainGeneric() {
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = strict.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectIncompatibleValues(false).build()) {
            assertEquals(Map.of("id", "00123"), strict.fromString(Map.class, "{\"id\":\"00123\"}"));
            assertNull(strict.fromString(Map.class, "null"));
            for (final var shape : List.of("42", "true", "false", "\"00123\"", "[]", "[1,{}]")) {
                assertThrows(JsonException.class, () -> strict.fromString(Map.class, shape), shape);
                assertEquals(strict.fromString(Object.class, shape), legacy.fromString((Type) Map.class, shape), shape);
            }
        }
    }

    @Test
    void rawMapsRequireObjectsWithoutRestrictingGenericObjectsOrCustomCodecs() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
            for (final var json : List.of("[]", "42", "true", "\"text\"")) {
                final var generic = mapper.fromString(Object.class, json);
                assertThrows(JsonException.class, () -> mapper.fromString(Map.class, json));
                assertThrows(JsonException.class, () -> mapper.fromString((Type) Map.class, json));
                assertEquals(generic, legacy.<Object>fromString((Type) Map.class, json));
            }
            assertEquals(Map.of("x", BigDecimal.ONE), mapper.fromString(Map.class, "{\"x\":1}"));
        }
        final JsonCodec<Map<String, String>> custom = new JsonCodec<>() {
            @Override
            public Type type() {
                return Map.class;
            }

            @Override
            public Map<String, String> read(final DeserializationContext context) {
                context.parser().enforceNext(VALUE_STRING);
                return Map.of("custom", context.parser().getString());
            }

            @Override
            public void write(final Map<String, String> value, final SerializationContext context) {
                throw new UnsupportedOperationException();
            }
        };
        try (final var mapper = new JsonMapperImpl(List.of(custom), key -> Optional.empty())) {
            assertEquals(Map.of("custom", "value"), mapper.fromString(Map.class, "\"value\""));
        }
    }

    @Test
    void numericRootsRejectQuotedNumbersUnlessLegacyCoercionIsEnabled() {
        final Map<Class<?>, Object> numbers = Map.of(
                Integer.class, 42, Long.class, 42L, Float.class, 42f, Double.class, 42d,
                int.class, 42, long.class, 42L, float.class, 42f, double.class, 42d);
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = strict.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectIncompatibleValues(false).build()) {
            numbers.forEach((type, expected) -> {
                assertEquals(expected, strict.fromString(type, "42"), type.getName());
                assertEquals(expected, legacy.fromString(type, "42"), type.getName());
                assertThrows(JsonException.class, () -> strict.fromString(type, "\"42\""), type.getName());
                assertEquals(expected, legacy.fromString(type, "\"42\""), type.getName());
                for (final var json : List.of("true", "false", "{}", "[]")) {
                    assertThrows(JsonException.class, () -> strict.fromString(type, json), type + ": " + json);
                    assertThrows(JsonException.class, () -> legacy.fromString(type, json), type + ": " + json);
                }
            });
        }
    }

    @Test
    void stringAndBooleanRootsRejectWrongShapesInBothModes() {
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = strict.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectIncompatibleValues(false).build()) {
            for (final var mapper : List.of(strict, legacy)) {
                assertEquals("text", mapper.fromString(String.class, "\"text\""));
                for (final var json : List.of("42", "1.5", "true", "false", "{}", "[]")) {
                    assertThrows(JsonException.class, () -> mapper.fromString(String.class, json), json);
                }
                for (final var type : List.of(Boolean.class, boolean.class)) {
                    assertEquals(Boolean.TRUE, mapper.fromString(type, "true"));
                    assertEquals(Boolean.FALSE, mapper.fromString(type, "false"));
                    for (final var json : List.of("\"true\"", "\"false\"", "42", "0", "{}", "[]")) {
                        assertThrows(JsonException.class, () -> mapper.fromString(type, json), json);
                    }
                }
            }
        }
    }

    @Test
    void bigDecimalRetainsItsStringAndNumericContractInBothModes() {
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = strict.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectIncompatibleValues(false).build()) {
            for (final var mapper : List.of(strict, legacy)) {
                for (final var literal : List.of("0", "-42", "123.4500", "1.25e3", "-2E-4")) {
                    final var expected = new BigDecimal(literal);
                    assertEquals(expected, mapper.fromString(BigDecimal.class, literal), literal);
                    assertEquals(expected, mapper.fromString(BigDecimal.class, '"' + literal + '"'), literal);
                    assertEquals('"' + expected.toString() + '"', mapper.toString(expected));
                }
                for (final var json : List.of("true", "false", "{}", "[]")) {
                    assertThrows(IllegalStateException.class, () -> mapper.fromString(BigDecimal.class, json), json);
                }
                assertThrows(NumberFormatException.class, () -> mapper.fromString(BigDecimal.class, "\"text\""));
            }
        }
    }

    @Test
    void numberRootsAcceptAllNumericFormsAndNullInBothModes() {
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = strict.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectIncompatibleValues(false).build()) {
            for (final var mapper : List.of(strict, legacy)) {
                for (final var literal : List.of("0", "-42", "2147483648", "9223372036854775808",
                        "123.4500", "1e3", "-1.25E-3", "2E+4")) {
                    assertEquals(new BigDecimal(literal), mapper.fromString(Number.class, literal), literal);
                }
                assertNull(mapper.fromString(Number.class, "null"));
                for (final var json : List.of("\"42\"", "\"1e3\"", "\"text\"", "true", "false", "{}", "[]")) {
                    assertThrows(JsonException.class, () -> mapper.fromString(Number.class, json), json);
                }
            }
        }
    }

    @Test
    void objectRootsAcceptEveryJsonShapeInBothModes() {
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var legacy = strict.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectIncompatibleValues(false).build()) {
            for (final var mapper : List.of(strict, legacy)) {
                assertEquals("text", mapper.fromString(Object.class, "\"text\""));
                assertEquals(new BigDecimal("42"), mapper.fromString(Object.class, "42"));
                assertEquals(new BigDecimal("1.25e3"), mapper.fromString(Object.class, "1.25e3"));
                assertEquals(Boolean.TRUE, mapper.fromString(Object.class, "true"));
                assertEquals(Boolean.FALSE, mapper.fromString(Object.class, "false"));
                assertNull(mapper.fromString(Object.class, "null"));
                assertEquals(Map.of(), mapper.fromString(Object.class, "{}"));
                assertEquals(List.of(), mapper.fromString(Object.class, "[]"));
                assertEquals(Map.of("items", List.of(new BigDecimal("1"), "two", true)),
                        mapper.fromString(Object.class, "{\"items\":[1,\"two\",true]}"));
                assertEquals(List.of(Map.of("value", false), new BigDecimal("2.5")),
                        mapper.fromString(Object.class, "[{\"value\":false},2.5]"));
            }
        }
    }

    @Test
    void configurationDefaultsToStrictAndCanExplicitlyEnableOrDisableIt() {
        for (final var configuration : List.of(Map.<String, String>of(),
                Map.of("fusion.json.rejectIncompatibleValues", "true"),
                Map.of("fusion.json.rejectIncompatibleValues", "false"))) {
            try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.ofNullable(configuration.get(key)))) {
                assertEquals(42, mapper.fromString(Integer.class, "42"));
                if ("false".equals(configuration.get("fusion.json.rejectIncompatibleValues"))) {
                    assertEquals(42, mapper.fromString(Integer.class, "\"42\""));
                } else {
                    assertThrows(JsonException.class, () -> mapper.fromString(Integer.class, "\"42\""));
                }
            }
        }
    }

    @Test
    void buildersAndDescendantsHaveIsolatedSettings() {
        try (final var parent = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var builder = parent.as(JsonMapper.Configuring.class).orElseThrow();
            try (final var child = builder.rejectIncompatibleValues(false).build();
                 final var sibling = builder.rejectIncompatibleValues(true).build();
                 final var inheritedChild = child.as(JsonMapper.Configuring.class).orElseThrow().build();
                 final var grandchild = child.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectIncompatibleValues(true).build()) {
                assertThrows(JsonException.class, () -> parent.fromString(Integer.class, "\"42\""));
                assertEquals(42, child.fromString(Integer.class, "\"42\""));
                assertThrows(JsonException.class, () -> sibling.fromString(Integer.class, "\"42\""));
                assertEquals(42, inheritedChild.fromString(Integer.class, "\"42\""));
                assertThrows(JsonException.class, () -> grandchild.fromString(Integer.class, "\"42\""));
                assertEquals(42, child.fromString(Integer.class, "\"42\""));
            }
            assertThrows(JsonException.class, () -> parent.fromString(Integer.class, "\"42\""));
            assertEquals(42, parent.fromString(Integer.class, "42"));
        }
    }

    @Test
    void descendantsDoNotCloseSharedCodecsOwnedByTheParent() {
        final var codec = new CloseableStringCodec();
        try (final var parent = new JsonMapperImpl(List.of(codec), key -> Optional.empty())) {
            try (final var child = parent.as(JsonMapper.Configuring.class).orElseThrow()
                    .rejectIncompatibleValues(false).build()) {
                try (final var grandchild = child.as(JsonMapper.Configuring.class).orElseThrow()
                        .rejectIncompatibleValues(true).build()) {
                    assertEquals("grandchild", grandchild.fromString(String.class, "\"grandchild\""));
                }
                assertEquals(0, codec.closes.get());
                assertEquals("child", child.fromString(String.class, "\"child\""));
            }
            assertEquals(0, codec.closes.get());
            assertEquals("parent", parent.fromString(String.class, "\"parent\""));
        }
        assertEquals(1, codec.closes.get());
    }

    private static class CloseableStringCodec implements JsonCodec<String>, AutoCloseable {
        private final AtomicInteger closes = new AtomicInteger();

        @Override
        public Type type() {
            return String.class;
        }

        @Override
        public String read(final DeserializationContext context) {
            assertEquals(0, closes.get());
            assertEquals(VALUE_STRING, context.parser().next());
            return context.parser().getString();
        }

        @Override
        public void write(final String value, final SerializationContext context) throws IOException {
            throw new UnsupportedOperationException("Read-only test codec");
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
