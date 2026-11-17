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
import io.yupiik.fusion.json.deserialization.AvailableCharArrayReader;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.json.spi.Parser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.CharBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.yupiik.fusion.json.spi.Parser.Event.END_OBJECT;
import static io.yupiik.fusion.json.spi.Parser.Event.KEY_NAME;
import static io.yupiik.fusion.json.spi.Parser.Event.START_OBJECT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDocumentTest {
    @Test
    void configurationDefaultsToRejectionAndOnlyDisablesSuffixValidation() {
        for (final var settings : List.of(Map.<String, String>of(),
                Map.of("fusion.json.rejectTrailingTokens", "true"),
                Map.of("fusion.json.rejectTrailingTokens", "false"))) {
            try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.ofNullable(settings.get(key)))) {
                for (final var input : Input.values()) {
                    assertEquals(Map.of(), input.read(mapper, Object.class, "{} \t\r\n"));
                    if ("false".equals(settings.get("fusion.json.rejectTrailingTokens"))) {
                        assertEquals(Map.of(), input.read(mapper, Object.class, "{} x"));
                    } else {
                        assertThrows(JsonException.class, () -> input.read(mapper, Object.class, "{} x"));
                    }
                    for (final var json : List.of("", " \t\r\n", "{", "[1")) {
                        assertThrows(JsonException.class, () -> input.read(mapper, Object.class, json));
                    }
                }
            }
        }
    }

    @Test
    void everyRootShapeRequiresOneCompleteDocumentOnFirstAndCachedReads() {
        for (final var root : roots()) {
            for (final var input : Input.values()) {
                if (!input.supports(root.type())) {
                    continue;
                }
                // Each suffix starts with a fresh mapper: typed container codec creation must not bypass validation.
                for (final var suffix : List.of("", " \t\r\n", "x", " false", " {}", " []",
                        " \"second\"", " 42", " null", " /* comment */", " // comment\n", "\0", " \0")) {
                    try (final var mapper = mapper()) {
                        for (int attempt = 0; attempt < 3; attempt++) {
                            final var json = root.json() + suffix;
                            final var message = input + ", type=" + root.type() + ", suffix=" + suffix + ", attempt=" + attempt;
                            if (suffix.isEmpty() || " \t\r\n".equals(suffix)) {
                                assertEquals(root.value(), input.read(mapper, root.type(), json), message);
                            } else {
                                assertThrows(JsonException.class, () -> input.read(mapper, root.type(), json), message);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void optOutAcceptsSuffixesAfterACompleteRootThroughEveryEntryPoint() {
        try (final var parent = mapper();
             final var configured = parent.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectTrailingTokens(false).build();
             final var property = new JsonMapperImpl(List.of(), key ->
                     "fusion.json.rejectTrailingTokens".equals(key) ? Optional.of("false") : Optional.empty())) {
            for (final var mapper : List.of(configured, property)) {
                for (final var root : roots()) {
                    for (final var input : Input.values()) {
                        if (!input.supports(root.type())) {
                            continue;
                        }
                        // Delimit scalars so the suffix is not part of a malformed first token.
                        for (final var suffix : List.of(" x", " false", " {}", " [", " /* comment */", " \0")) {
                            for (int attempt = 0; attempt < 2; attempt++) {
                                assertEquals(root.value(), input.read(mapper, root.type(), root.json() + suffix),
                                        input + ", type=" + root.type() + ", suffix=" + suffix);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void emptyTruncatedAndMalformedFirstRootsFailEvenWithSuffixValidationDisabled() {
        try (final var strict = mapper();
             final var permissive = strict.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectTrailingTokens(false).build()) {
            for (final var mapper : List.of(strict, permissive)) {
                for (final var input : Input.values()) {
                    for (final var json : List.of("", " \t\r\n", "{", "[", "[1", "[1,", "{\"a\":",
                            "{\"a\":1", "{\"a\":[{}", "[1,]", "{\"a\":1,}", "[1 2]", "{\"a\" 1}",
                            "\"", "\"abc", "\"\\", "\"\\u000", "\"\\q\"", "tru", "fals", "nul", "-", "1.", "1e+",
                            "\0", "[\0]", "\"raw\0nul\"")) {
                        assertThrows(JsonException.class, () -> input.read(mapper, Object.class, json), input + ": " + json);
                    }
                    for (final var root : roots()) {
                        if (input.supports(root.type())) {
                            assertThrows(JsonException.class, () -> input.read(mapper, root.type(), ""),
                                    input + ", type=" + root.type());
                        }
                    }
                }
            }
        }
    }

    @Test
    void onlyJsonWhitespaceIsAcceptedAndEscapedNulRemainsAValidString() {
        try (final var mapper = mapper()) {
            for (final var input : Input.values()) {
                assertEquals("a\0b", input.read(mapper, String.class, " \t\r\n\"a\\u0000b\" \t\r\n"));
                assertEquals("a\0b", input.read(mapper, Object.class, "\"a\\u0000b\""));
                for (final var whitespace : List.of("\f", "\u000b", "\u0085", "\u00a0", "\u2003", "\ufeff")) {
                    for (final var json : List.of(whitespace + "{}", "{}" + whitespace, "[1," + whitespace + "2]")) {
                        assertThrows(JsonException.class, () -> input.read(mapper, Object.class, json), input + ": " + json);
                    }
                }
            }
        }
    }

    @Test
    void skippedUnknownSubtreesMustBeWellFormedAndComplete() {
        try (final var strict = new JsonMapperImpl(List.of(new SkippingCodec()), key -> Optional.empty());
             final var permissive = strict.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectTrailingTokens(false).build()) {
            for (final var mapper : List.of(strict, permissive)) {
                for (final var input : Input.values()) {
                    assertEquals(new Model(), input.read(mapper, Model.class,
                            "{\"unknown\":[{},[1,true,null],{\"nested\":[]}]}"));
                    for (final var json : List.of("{\"unknown\":[", "{\"unknown\":{", "{\"unknown\":[{\"x\":1}",
                            "{\"unknown\":{\"nested\":[1,2]}", "{\"unknown\":[1,]}",
                            "{\"unknown\":{\"x\" 1}}", "{\"unknown\":[\"raw\0nul\"]}")) {
                        assertThrows(JsonException.class, () -> input.read(mapper, Model.class, json), input + ": " + json);
                    }
                }
            }
        }
    }

    @Test
    void partialAndNoInputCodecsFailWithoutDrainingEvenWhenTrailingTokensAreAllowed() {
        for (final var consumeStart : List.of(false, true)) {
            for (final var reject : List.of(false, true)) {
                final var events = new AtomicInteger();
                final var closes = new AtomicInteger();
                try (final var parent = new JsonMapperImpl(List.of(new PartialCodec(consumeStart)), key -> Optional.empty(),
                        reader -> new ForwardingParser(reader, events, closes));
                     final var mapper = parent.as(JsonMapper.Configuring.class).orElseThrow()
                             .rejectTrailingTokens(reject).build()) {
                    for (final var json : List.of("{}", "{\"stillUnread\":[1,2,3]}", "{", "")) {
                        events.set(0);
                        final int previousCloses = closes.get();
                        assertThrows(JsonException.class, () -> mapper.fromString(Model.class, json),
                                "consumeStart=" + consumeStart + ", reject=" + reject + ", json=" + json);
                        // Root null detection peeks once; a codec can replay that opening event.
                        // Neither operation consumes any member or closing event from the unread root.
                        assertTrue(events.get() <= (consumeStart ? 2 : 1), "Mapper must not drain the unread root");
                        assertEquals(previousCloses + 1, closes.get());
                    }
                }
            }
        }
    }

    @Test
    void builtInParserRejectsPartialAndMultipleRootCodecsWithEitherDocumentPolicy() {
        for (final var reject : List.of(false, true)) {
            for (final var consumeStart : List.of(false, true)) {
                try (final var parent = new JsonMapperImpl(List.of(new PartialCodec(consumeStart)), key -> Optional.empty());
                     final var mapper = parent.as(JsonMapper.Configuring.class).orElseThrow()
                             .rejectTrailingTokens(reject).build()) {
                    for (final var input : Input.values()) {
                        assertThrows(JsonException.class, () -> input.read(mapper, Model.class, "{}"));
                        assertThrows(JsonException.class, () -> input.read(mapper, Model.class, "{\"unread\":1}"));
                    }
                }
            }
            final var codec = new SkippingCodec() {
                @Override
                public Model read(final DeserializationContext context) {
                    context.parser().next();
                    context.parser().next();
                    return new Model();
                }
            };
            try (final var parent = new JsonMapperImpl(List.of(codec), key -> Optional.empty());
                 final var mapper = parent.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectTrailingTokens(reject).build()) {
                for (final var input : Input.values()) {
                    assertThrows(JsonException.class, () -> input.read(mapper, Model.class, "true false"));
                }
            }
        }
    }

    @Test
    void customParserFactoryWithHonestEofSupportsDocumentValidation() {
        final var events = new AtomicInteger();
        final var closes = new AtomicInteger();
        try (final var strict = new JsonMapperImpl(List.of(), key -> Optional.empty(),
                reader -> new ForwardingParser(reader, events, closes));
             final var permissive = strict.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectTrailingTokens(false).build()) {
            for (final var input : Input.values()) {
                for (final var root : roots()) {
                    if (!input.supports(root.type())) {
                        continue;
                    }
                    assertEquals(root.value(), input.read(strict, root.type(), root.json() + " \t\r\n"));
                    assertThrows(JsonException.class, () -> input.read(strict, root.type(), root.json() + " false"));
                    assertThrows(JsonException.class, () -> input.read(strict, root.type(), root.json() + " x"));
                    assertEquals(root.value(), input.read(permissive, root.type(), root.json() + " false"));
                }
            }
        }
        assertTrue(events.get() > 0);
        assertTrue(closes.get() > 0);
    }

    @Test
    void builderInheritanceIsIsolatedAndStrictFlagsAreIndependent() {
        try (final var parent = mapper()) {
            final var builder = parent.as(JsonMapper.Configuring.class).orElseThrow();
            try (final var permissive = builder.rejectTrailingTokens(false).build();
                 final var sibling = builder.rejectTrailingTokens(true).build();
                 final var inherited = permissive.as(JsonMapper.Configuring.class).orElseThrow().build();
                 final var strictChild = permissive.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectTrailingTokens(true).build();
                 final var duplicates = permissive.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectDuplicateKeys(true).build();
                 final var looseValues = sibling.as(JsonMapper.Configuring.class).orElseThrow()
                         .rejectIncompatibleValues(false).build()) {
                for (final var mapper : List.of(parent, sibling, strictChild, looseValues)) {
                    assertThrows(JsonException.class, () -> mapper.fromString(Object.class, "{} false"));
                }
                for (final var mapper : List.of(permissive, inherited, duplicates)) {
                    assertEquals(Map.of(), mapper.fromString(Object.class, "{} false"));
                    assertThrows(JsonException.class, () -> mapper.fromString(Integer.class, "\"wrong\""));
                }
                assertThrows(JsonException.class, () -> duplicates.fromString(Object.class, "{\"x\":1,\"x\":2} false"));
                assertEquals(Map.of("x", new BigDecimal("2")), permissive.fromString(Object.class, "{\"x\":1,\"x\":2} false"));
            }
            assertThrows(JsonException.class, () -> parent.fromString(Object.class, "{} false"));
        }
        try (final var parent = new JsonMapperImpl(List.of(), key ->
                "fusion.json.rejectTrailingTokens".equals(key) ? Optional.of("false") : Optional.empty());
             final var child = parent.as(JsonMapper.Configuring.class).orElseThrow().build();
             final var strict = parent.as(JsonMapper.Configuring.class).orElseThrow().rejectTrailingTokens(true).build()) {
            assertEquals(Map.of(), child.fromString(Object.class, "{} false"));
            assertThrows(JsonException.class, () -> strict.fromString(Object.class, "{} false"));
            assertEquals(Map.of(), parent.fromString(Object.class, "{} false"));
        }
    }

    @Test
    void readerAndInputStreamAreClosedOnSuccessAndDocumentFailure() {
        try (final var mapper = mapper()) {
            for (final var json : List.of("{}", "{} x", "{", "")) {
                final var readerCloses = new AtomicInteger();
                final var streamCloses = new AtomicInteger();
                final var reader = new ChunkReader(json, 1) {
                    @Override
                    public void close() {
                        readerCloses.incrementAndGet();
                    }
                };
                final var stream = new ByteArrayInputStream(json.getBytes(UTF_8)) {
                    @Override
                    public void close() {
                        streamCloses.incrementAndGet();
                    }
                };
                if ("{}".equals(json)) {
                    assertEquals(Map.of(), mapper.read(Object.class, reader));
                    assertEquals(Map.of(), mapper.read((Type) Object.class, stream));
                } else {
                    assertThrows(JsonException.class, () -> mapper.read(Object.class, reader));
                    assertThrows(JsonException.class, () -> mapper.read((Type) Object.class, stream));
                }
                assertEquals(1, readerCloses.get());
                assertEquals(1, streamCloses.get());
            }
        }
    }

    private JsonMapper mapper() {
        return new JsonMapperImpl(List.of(), key -> Optional.empty());
    }

    private List<Root> roots() {
        return List.of(
                new Root(Object.class, "{}", Map.of()),
                new Root(Object.class, "[]", List.of()),
                new Root(Object.class, "{\"x\":[1,true,{\"y\":\"z\"}]}", Map.of("x", List.of(BigDecimal.ONE, true, Map.of("y", "z")))),
                new Root(Object.class, "\"text\"", "text"),
                new Root(Object.class, "-12.5e+2", new BigDecimal("-12.5e+2")),
                new Root(Object.class, "true", true),
                new Root(Object.class, "false", false),
                new Root(Object.class, "null", null),
                new Root(String.class, "\"text\"", "text"),
                new Root(String.class, "null", null),
                new Root(Number.class, "123", new BigDecimal("123")),
                new Root(Number.class, "null", null),
                new Root(Boolean.class, "true", true),
                new Root(Boolean.class, "false", false),
                new Root(Boolean.class, "null", null),
                new Root(Map.class, "{\"x\":1}", Map.of("x", BigDecimal.ONE)),
                new Root(new Types.ParameterizedTypeImpl(List.class, String.class), "[]", List.of()),
                new Root(new Types.ParameterizedTypeImpl(List.class, String.class), "null", null),
                new Root(new Types.ParameterizedTypeImpl(List.class, String.class), "[\"a\",\"b\"]", List.of("a", "b")),
                new Root(new Types.ParameterizedTypeImpl(Map.class, String.class, Integer.class), "{}", Map.of()),
                new Root(new Types.ParameterizedTypeImpl(Map.class, String.class, Integer.class), "null", null),
                new Root(new Types.ParameterizedTypeImpl(Map.class, String.class, Integer.class), "{\"x\":1}", Map.of("x", 1)),
                new Root(new Types.ParameterizedTypeImpl(List.class, new Types.ParameterizedTypeImpl(Map.class, String.class, Boolean.class)),
                        "[{\"x\":true}]", List.of(Map.of("x", true))));
    }

    private record Root(Type type, String json, Object value) {
    }

    private enum Input {
        STRING_CLASS, STRING_TYPE, BYTES_CLASS, BYTES_TYPE, READER_CLASS, READER_TYPE,
        TINY_READER_CLASS, TINY_READER_TYPE, ARRAY_READER_CLASS, ARRAY_READER_TYPE, STREAM_CLASS, STREAM_TYPE;

        private boolean supports(final Type type) {
            return !name().endsWith("_CLASS") || type instanceof Class<?>;
        }

        private Object read(final JsonMapper mapper, final Type type, final String json) {
            return switch (this) {
                case STRING_CLASS -> mapper.fromString((Class<?>) type, json);
                case STRING_TYPE -> mapper.fromString(type, json);
                case BYTES_CLASS -> mapper.fromBytes((Class<?>) type, json.getBytes(UTF_8));
                case BYTES_TYPE -> mapper.fromBytes(type, json.getBytes(UTF_8));
                case READER_CLASS -> mapper.read((Class<?>) type, new ChunkReader(json, 3));
                case READER_TYPE -> mapper.read(type, new ChunkReader(json, 3));
                case TINY_READER_CLASS -> mapper.read((Class<?>) type, new ChunkReader(json, 1));
                case TINY_READER_TYPE -> mapper.read(type, new ChunkReader(json, 1));
                case ARRAY_READER_CLASS -> mapper.read((Class<?>) type, new AvailableCharArrayReader(json.toCharArray()));
                case ARRAY_READER_TYPE -> mapper.read(type, new AvailableCharArrayReader(json.toCharArray()));
                case STREAM_CLASS -> mapper.read((Class<?>) type, new ByteArrayInputStream(json.getBytes(UTF_8)));
                case STREAM_TYPE -> mapper.read(type, new ByteArrayInputStream(json.getBytes(UTF_8)));
            };
        }
    }

    private static class ChunkReader extends Reader {
        private final String json;
        private final int chunk;
        private int position;

        private ChunkReader(final String json, final int chunk) {
            this.json = json;
            this.chunk = chunk;
        }

        @Override
        public int read(final char[] target, final int offset, final int length) {
            if (length == 0) {
                return 0;
            }
            if (position == json.length()) {
                return -1;
            }
            final int count = Math.min(Math.min(chunk, length), json.length() - position);
            json.getChars(position, position + count, target, offset);
            position += count;
            return count;
        }

        @Override
        public void close() {
            // No external resource.
        }
    }

    private record Model() {
    }

    private static class SkippingCodec implements JsonCodec<Model> {
        @Override
        public Type type() {
            return Model.class;
        }

        @Override
        public Model read(final DeserializationContext context) {
            final var parser = context.parser();
            parser.enforceNext(START_OBJECT);
            while (parser.hasNext()) {
                final var event = parser.next();
                if (event == END_OBJECT) {
                    return new Model();
                }
                assertEquals(KEY_NAME, event);
                switch (parser.next()) {
                    case START_OBJECT -> parser.skipObject();
                    case START_ARRAY -> parser.skipArray();
                    default -> {
                        // Unknown scalar already consumed.
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

    private static class PartialCodec extends SkippingCodec {
        private final boolean consumeStart;

        private PartialCodec(final boolean consumeStart) {
            this.consumeStart = consumeStart;
        }

        @Override
        public Model read(final DeserializationContext context) {
            if (consumeStart) {
                context.parser().enforceNext(START_OBJECT);
            }
            return new Model();
        }
    }

    // Deliberately not a JsonParser subtype: completion must work through the public Parser contract.
    private static class ForwardingParser implements Parser {
        private final JsonParser delegate;
        private final AtomicInteger events;
        private final AtomicInteger closes;

        private ForwardingParser(final Reader reader, final AtomicInteger events, final AtomicInteger closes) {
            this.delegate = new JsonParser(reader, 8, new BufferProvider(8, -1), true);
            this.events = events;
            this.closes = closes;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Event next() {
            final var event = delegate.next();
            events.incrementAndGet();
            return event;
        }

        @Override
        public void rewind(final Event event) {
            delegate.rewind(event);
        }

        @Override
        public void enforceNext(final Event event) {
            if (!hasNext() || next() != event) {
                throw new JsonException("Expected " + event);
            }
        }

        @Override
        public String getString() {
            return delegate.getString();
        }

        @Override
        public CharBuffer getChars() {
            return delegate.getChars();
        }

        @Override
        public boolean isInArray() {
            return delegate.isInArray();
        }

        @Override
        public boolean isInObject() {
            return delegate.isInObject();
        }

        @Override
        public void skipObject() {
            delegate.skipObject();
        }

        @Override
        public void skipArray() {
            delegate.skipArray();
        }

        @Override
        public int getInt() {
            return delegate.getInt();
        }

        @Override
        public long getLong() {
            return delegate.getLong();
        }

        @Override
        public double getDouble() {
            return delegate.getDouble();
        }

        @Override
        public BigDecimal getBigDecimal() {
            return delegate.getBigDecimal();
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            delegate.close();
        }
    }
}
