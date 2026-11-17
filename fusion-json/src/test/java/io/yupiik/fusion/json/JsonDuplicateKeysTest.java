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
import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.internal.parser.ValidatingParser;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.json.spi.Parser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static io.yupiik.fusion.json.spi.Parser.Event.END_ARRAY;
import static io.yupiik.fusion.json.spi.Parser.Event.END_OBJECT;
import static io.yupiik.fusion.json.spi.Parser.Event.KEY_NAME;
import static io.yupiik.fusion.json.spi.Parser.Event.START_ARRAY;
import static io.yupiik.fusion.json.spi.Parser.Event.START_OBJECT;
import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_NULL;
import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_NUMBER;
import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDuplicateKeysTest {
    @Test
    void decodedNamesAreComparedEvenWhenTheFirstValueIsNull() {
        for (final var json : List.of("{\"x\":null,\"x\":false}",
                "{\"x\":1,\"\\u0078\":2}", "{\"a/b\":1,\"a\\/b\":2}",
                "{\"\\u00e9\":1,\"\\u00E9\":2}", "{\"\":null,\"\":null}")) {
            try (final var parser = validating(json, true)) {
                parser.enforceNext(START_OBJECT);
                final var error = assertThrows(JsonException.class, parser::skipObject, json);
                assertTrue(error.getMessage().contains("Duplicate"), error.getMessage());
            }
        }
    }

    @Test
    void objectScopesAreIndependentAcrossNestingSiblingsAndArrays() {
        try (final var parser = validating(
                "{\"x\":[{\"x\":1},{\"x\":2},[\"x\",\"x\"]],\"child\":{\"x\":3}}", true)) {
            parser.enforceNext(START_OBJECT);
            parser.skipObject();
            assertFalse(parser.hasNext());
        }
        try (final var parser = validating("{\"x\":{\"x\":1},\"x\":2}", true)) {
            parser.enforceNext(START_OBJECT);
            assertThrows(JsonException.class, parser::skipObject);
        }
    }

    @Test
    void skippedArraysAndObjectsValidateAllNestedKeys() {
        for (final var json : List.of("[{\"x\":1,\"x\":2}]",
                "[[{\"x\":1,\"x\":2}]]", "[{\"nested\":[{\"x\":1,\"x\":2}]}]")) {
            try (final var parser = validating(json, true)) {
                parser.enforceNext(START_ARRAY);
                assertThrows(JsonException.class, parser::skipArray, json);
            }
        }
    }

    @Test
    void skipsConsumeOnlyTheAlreadyOpenedContainer() {
        try (final var parser = validating("[{\"a\":[{\"x\":1}],\"b\":2},{\"a\":[]}]", true)) {
            parser.skipObject(); // no object has been opened
            parser.skipArray(); // no array has been opened
            parser.enforceNext(START_ARRAY);
            parser.enforceNext(START_OBJECT);
            parser.enforceNext(KEY_NAME);
            parser.enforceNext(START_ARRAY);
            parser.skipArray();
            parser.enforceNext(KEY_NAME);
            assertEquals("b", parser.getString());
            parser.enforceNext(VALUE_NUMBER);
            parser.enforceNext(END_OBJECT);
            parser.enforceNext(START_OBJECT);
            parser.skipObject();
            parser.enforceNext(END_ARRAY);
            assertFalse(parser.hasNext());
        }
    }

    @Test
    void repeatedRewindsDoNotPushPopOrRecordAnEventTwice() {
        try (final var parser = validating("[{\"x\":null},{\"x\":1,\"x\":2}]", true)) {
            replay(parser, START_ARRAY);
            replay(parser, START_OBJECT);
            replay(parser, KEY_NAME);
            assertEquals("x", parser.getString());
            replay(parser, VALUE_NULL);
            replay(parser, END_OBJECT);
            replay(parser, START_OBJECT);
            replay(parser, KEY_NAME);
            replay(parser, VALUE_NUMBER);
            assertThrows(JsonException.class, () -> parser.enforceNext(KEY_NAME));
        }
        try (final var parser = validating("[{\"x\":null}]", true)) {
            replay(parser, START_ARRAY);
            replay(parser, START_OBJECT);
            replay(parser, KEY_NAME);
            replay(parser, VALUE_NULL);
            replay(parser, END_OBJECT);
            replay(parser, END_ARRAY);
            assertFalse(parser.hasNext());
        }
    }

    @Test
    void disabledValidationDoesNotMaterializeNames() {
        final var calls = new AtomicInteger();
        try (final var parser = new ValidatingParser(new JsonParser(
                new StringReader("{\"x\":null,\"x\":false}"), 128, new BufferProvider(128, -1), true) {
            @Override
            public String getString() {
                calls.incrementAndGet();
                return super.getString();
            }
        }, false)) {
            parser.enforceNext(START_OBJECT);
            parser.skipObject();
            assertFalse(parser.hasNext());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void accessorsMatchingAndCloseAreDelegated() {
        final var closes = new AtomicInteger();
        final var matches = new AtomicInteger();
        try (final var parser = new ValidatingParser(new JsonParser(
                new StringReader("{\"x\":[\"text\",42]}"), 128, new BufferProvider(128, -1), true) {
            @Override
            public int matchString(final char[][] candidates, final IntUnaryOperator offsets) {
                matches.incrementAndGet();
                return super.matchString(candidates, offsets);
            }

            @Override
            public void close() {
                closes.incrementAndGet();
                super.close();
            }
        }, true)) {
            parser.enforceNext(START_OBJECT);
            assertTrue(parser.isInObject());
            parser.enforceNext(KEY_NAME);
            assertEquals(0, parser.matchString(new char[][]{{'x'}}, length -> length == 1 ? 0 : -1));
            assertEquals(1, matches.get());
            parser.enforceNext(START_ARRAY);
            assertTrue(parser.isInArray());
            parser.enforceNext(VALUE_STRING);
            assertEquals("text", parser.getString());
            assertEquals("text", parser.getChars().toString());
            parser.enforceNext(VALUE_NUMBER);
            assertEquals(42, parser.getInt());
            assertEquals(42L, parser.getLong());
            assertEquals(42d, parser.getDouble());
            assertEquals(new BigDecimal("42"), parser.getBigDecimal());
            parser.enforceNext(END_ARRAY);
            parser.enforceNext(END_OBJECT);
            assertFalse(parser.isInObject());
            assertFalse(parser.isInArray());
            assertThrows(JsonException.class, () -> parser.enforceNext(KEY_NAME));
        }
        assertEquals(1, closes.get());
        try (final var parser = validating("[]", true)) {
            assertThrows(JsonException.class, () -> parser.enforceNext(START_OBJECT));
        }
    }

    @Test
    void configurationDefaultsToAllowingDuplicatesAndCanEnableRejection() {
        for (final var settings : List.of(Map.<String, String>of(),
                Map.of("fusion.json.rejectDuplicateKeys", "false"),
                Map.of("fusion.json.rejectDuplicateKeys", "true"))) {
            try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.ofNullable(settings.get(key)))) {
                final var json = "{\"x\":null,\"x\":2}";
                if ("true".equals(settings.get("fusion.json.rejectDuplicateKeys"))) {
                    assertThrows(JsonException.class, () -> mapper.fromString(Object.class, json));
                } else {
                    assertEquals(Map.of("x", new BigDecimal("2")), mapper.fromString(Object.class, json));
                }
            }
        }
    }

    @Test
    void buildersInheritSettingsWithoutChangingParentsOrSiblings() {
        try (final var parent = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var builder = parent.as(JsonMapper.Configuring.class).orElseThrow();
            try (final var strict = builder.rejectDuplicateKeys(true).build();
                 final var sibling = builder.rejectDuplicateKeys(false).build();
                 final var inheritedStrict = strict.as(JsonMapper.Configuring.class).orElseThrow().build();
                 final var inheritedDefault = parent.as(JsonMapper.Configuring.class).orElseThrow().build();
                 final var permissiveChild = strict.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectDuplicateKeys(false).build()) {
                final var json = "{\"x\":1,\"x\":2}";
                for (final var mapper : List.of(parent, sibling, inheritedDefault, permissiveChild)) {
                    assertEquals(Map.of("x", new BigDecimal("2")), mapper.fromString(Object.class, json));
                }
                for (final var mapper : List.of(strict, inheritedStrict)) {
                    assertThrows(JsonException.class, () -> mapper.fromString(Object.class, json));
                }
                // Reusing the builder must not mutate previously built children.
                assertThrows(JsonException.class, () -> strict.fromString(Object.class, json));
            }
            assertEquals(Map.of("x", new BigDecimal("2")),
                    parent.fromString(Object.class, "{\"x\":1,\"x\":2}"));
        }
        try (final var parent = new JsonMapperImpl(List.of(),
                key -> "fusion.json.rejectDuplicateKeys".equals(key) ? Optional.of("true") : Optional.empty());
             final var child = parent.as(JsonMapper.Configuring.class).orElseThrow().build();
             final var permissive = parent.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectDuplicateKeys(false).build()) {
            assertThrows(JsonException.class, () -> parent.fromString(Object.class, "{\"x\":0,\"x\":1}"));
            assertThrows(JsonException.class, () -> child.fromString(Object.class, "{\"x\":0,\"x\":1}"));
            assertEquals(Map.of("x", BigDecimal.ONE),
                    permissive.fromString(Object.class, "{\"x\":0,\"x\":1}"));
        }
    }

    @Test
    void genericObjectAndTypedMapsRejectDuplicatesWithBothParserFactories() {
        for (final var custom : List.of(false, true)) {
            try (final var parent = mapper(custom);
                 final var strict = parent.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectDuplicateKeys(true).build()) {
                final var mapType = new Types.ParameterizedTypeImpl(Map.class, String.class, Object.class);
                for (final var type : List.<Type>of(Object.class, mapType)) {
                    for (final var json : List.of("{\"x\":null,\"x\":1}",
                            "{\"x\":1,\"\\u0078\":2}",
                            "{\"nested\":[{\"x\":1,\"x\":2}]}")) {
                        assertThrows(JsonException.class, () -> strict.fromString(type, json), json);
                    }
                    assertEquals(Map.of("x", List.of(Map.of("x", BigDecimal.ONE),
                                    Map.of("x", new BigDecimal("2")))),
                            strict.fromString(type, "{\"x\":[{\"x\":1},{\"x\":2}]}"));
                }
                // Exercise the Class overload too, independently of Type dispatch.
                assertThrows(JsonException.class,
                        () -> strict.fromString(Object.class, "{\"x\":null,\"x\":1}"));
                assertEquals(List.of("x", "x", Map.of("x", BigDecimal.ONE), Map.of("x", BigDecimal.ONE)),
                        strict.fromString(Object.class, "[\"x\",\"x\",{\"x\":1},{\"x\":1}]"));
            }
        }
    }

    @Test
    void modelsAndModelArraysRejectKnownAndSkippedUnknownDuplicatesWithCustomFactories() {
        for (final var custom : List.of(false, true)) {
            try (final var parent = mapper(custom);
                 final var strict = parent.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectDuplicateKeys(true).build()) {
                for (final var json : List.of("{\"name\":\"a\",\"name\":\"b\"}",
                        "{\"unknown\":null,\"unknown\":null}",
                        "{\"unknown\":{\"x\":null,\"x\":1}}",
                        "{\"unknown\":[[{\"x\":1,\"x\":2}]]}",
                        "{\"unknown\":{\"nested\":[{\"x\":1,\"\\u0078\":2}]}}")) {
                    assertThrows(JsonException.class, () -> strict.fromString(Model.class, json), json);
                    final var models = new Types.ParameterizedTypeImpl(List.class, Model.class);
                    assertThrows(JsonException.class, () -> strict.fromString(models, '[' + json + ']'), json);
                }
                assertEquals(new Model("ok"), strict.fromString(Model.class,
                        "{\"unknown\":[{\"x\":1},{\"x\":2}],\"name\":\"ok\"}"));
                assertEquals(List.of(new Model("a"), new Model("b")),
                        strict.fromString(new Types.ParameterizedTypeImpl(List.class, Model.class),
                                "[{\"name\":\"a\"},{\"name\":\"b\"}]"));
            }
        }
    }

    private JsonMapper mapper(final boolean custom) {
        if (custom) {
            return new JsonMapperImpl(List.of(new ModelCodec()), key -> Optional.empty(), this::customParser);
        }
        return new JsonMapperImpl(List.of(new ModelCodec()), key -> Optional.empty());
    }

    private record Model(String name) {
    }

    // Minimal model reader with the same unknown-property skipping contract as generated codecs.
    private static class ModelCodec implements JsonCodec<Model> {
        @Override
        public Type type() {
            return Model.class;
        }

        @Override
        public Model read(final DeserializationContext context) {
            final var parser = context.parser();
            parser.enforceNext(START_OBJECT);
            String name = null;
            while (parser.hasNext()) {
                final var event = parser.next();
                if (event == END_OBJECT) {
                    return new Model(name);
                }
                assertEquals(KEY_NAME, event);
                final var key = parser.getString();
                if ("name".equals(key)) {
                    parser.enforceNext(VALUE_STRING);
                    name = parser.getString();
                } else {
                    switch (parser.next()) {
                        case START_OBJECT -> parser.skipObject();
                        case START_ARRAY -> parser.skipArray();
                        default -> {
                            // A scalar unknown value is already consumed.
                        }
                    }
                }
            }
            throw new JsonException("Unclosed model");
        }

        @Override
        public void write(final Model value, final SerializationContext context) {
            throw new UnsupportedOperationException("Read-only test codec");
        }
    }

    private void replay(final Parser parser, final Parser.Event event) {
        parser.enforceNext(event);
        for (int i = 0; i < 3; i++) {
            parser.rewind(event);
            assertTrue(parser.hasNext());
            parser.enforceNext(event);
        }
    }

    private ValidatingParser validating(final String json, final boolean reject) {
        return new ValidatingParser(customParser(new StringReader(json)), reject);
    }

    private JsonParser customParser(final Reader reader) {
        return new JsonParser(reader, 128, new BufferProvider(128, -1), true) {
            @Override
            public void enforceNext(final Event event) {
                throw new AssertionError("Validation must enforce through its own next()");
            }

            @Override
            public void skipObject() {
                throw new AssertionError("Validation must skip through its own next()");
            }

            @Override
            public void skipArray() {
                throw new AssertionError("Validation must skip through its own next()");
            }
        };
    }
}
