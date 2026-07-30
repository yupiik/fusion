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
import io.yupiik.fusion.json.pretty.PrettyJsonMapper;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.yupiik.fusion.json.spi.Parser.Event.END_OBJECT;
import static io.yupiik.fusion.json.spi.Parser.Event.KEY_NAME;
import static io.yupiik.fusion.json.spi.Parser.Event.START_OBJECT;
import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class JsonMapperTest {
    private final List<JsonCodec<?>> jsonCodecs = List.of(new JsonCodec<Simple>() {
        @Override
        public Type type() {
            return Simple.class;
        }

        @Override
        public Simple read(final DeserializationContext context) {
            final var reader = context.parser();
            assertEquals(START_OBJECT, reader.next());
            try {
                assertEquals(KEY_NAME, reader.next());
                assertEquals("name", reader.getString());
                assertEquals(VALUE_STRING, reader.next());
                return new Simple(reader.getString());
            } finally {
                assertEquals(END_OBJECT, reader.next());
            }
        }

        @Override
        public void write(final Simple value, final SerializationContext ctx) throws IOException {
            ctx.writer().write("{\"name\":\"" + value.name() + "\"}");
        }
    });

    @Test
    void generic() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            assertEquals(Map.of("name", "ok"), mapper.fromString(Object.class, "{\"name\": \"ok\"}"));
            assertEquals(Map.of("name", "ok"), mapper.fromString(Map.class, "{\"name\": \"ok\"}"));
        }
    }

    @Test
    void mapWithOnlyNullValues() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var map = new java.util.LinkedHashMap<String, Object>();
            map.put("a", null);
            map.put("b", null);
            assertEquals("{\"a\":null,\"b\":null}", mapper.toString(map));
        }
    }

    @Test
    void nonAsciiBytesRoundTrip() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            // 2 bytes (accents), 3 bytes (CJK), 4 bytes (surrogate pair) at the start, middle and end of the payload
            final var values = List.of(
                    "eéè accents", "你好 cjk", "😀 emoji",
                    "ascii prefix then é", "ascii only", "tail 😀");
            for (final var value : values) {
                final var map = Map.of("v", value);
                final var bytes = mapper.toBytes(map);
                assertEquals(mapper.toString(map).getBytes(java.nio.charset.StandardCharsets.UTF_8).length, bytes.length);
                assertEquals(map, mapper.fromBytes(Object.class, bytes));
                assertEquals(map, mapper.fromString(Object.class, mapper.toString(map)));
            }
        }
    }

    @Test
    void writeToOutputStream() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            for (final var value : List.of("plain ascii", "eéè accents", "你好 cjk", "😀 emoji")) {
                final var map = Map.of("v", value);
                final var out = new java.io.ByteArrayOutputStream();
                mapper.write(map, out);
                org.junit.jupiter.api.Assertions.assertArrayEquals(mapper.toBytes(map), out.toByteArray(), value);
            }
        }
    }

    @Test
    void readFromInputStream() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            for (final var value : List.of("plain ascii", "eéè accents", "你好 cjk", "😀 emoji")) {
                final var map = Map.of("v", value);
                final var bytes = mapper.toBytes(map);
                assertEquals(map, mapper.read(Object.class, new java.io.ByteArrayInputStream(bytes)));
            }
        }
    }

    @Test
    void escapedStringsRoundTrip() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var value = "quote:\" backslash:\\ tab:\t newline:\n control:" + (char) 1 + " plain";
            final var json = mapper.toString(Map.of("v", value));
            assertEquals("{\"v\":\"quote:\\\" backslash:\\\\ tab:\\t newline:\\n control:\\u0001 plain\"}", json);
            assertEquals(Map.of("v", value), mapper.fromString(Object.class, json));
        }
    }

    @Test
    void truncatedPayloadIsAParseErrorNotANpe() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty())) {
            final var truncated = "{\"name\": \"trunca".toCharArray();
            assertThrows(IllegalStateException.class, () -> mapper.read(
                    Object.class,
                    new io.yupiik.fusion.json.deserialization.AvailableCharArrayReader(truncated)));
        }
    }

    @Test
    void prettyMapper() {
        try (final var mapper = new PrettyJsonMapper(new JsonMapperImpl(jsonCodecs, key -> Optional.empty()))) {
            assertEquals("""
                    {
                      "name": "formatted"
                    }""", mapper.toString(new Simple("formatted")));
        }
    }

    @Test
    void directCodec() throws IOException {
        final var json = "{\"name\":\"hello\"}";
        try (final var mapper = new JsonMapperImpl(jsonCodecs, key -> Optional.empty());
             final var reader = new StringReader(json)) {

            final var simple = mapper.read(Simple.class, reader);
            assertEquals("hello", simple.name());

            try (final var out = new StringWriter()) {
                mapper.write(simple, out);
                out.flush();
                assertEquals(json, out.toString());
            }
        }
    }

    @Test
    void listCodec() throws IOException {
        final var json = "[{\"name\":\"hello\"},{\"name\":\"second\"}]";
        try (final var mapper = new JsonMapperImpl(jsonCodecs, key -> Optional.empty());
             final var reader = new StringReader(json)) {

            final var simple = mapper.read(new Types.ParameterizedTypeImpl(List.class, Simple.class), reader);
            assertEquals("[Simple[name=hello], Simple[name=second]]", simple.toString());

            try (final var out = new StringWriter()) {
                mapper.write(simple, out);
                out.flush();
                assertEquals(json, out.toString());
            }
        }
    }

    @Test
    void mapCodec() throws IOException {
        final var json = "{\"first\":{\"name\":\"hello\"},\"second\":{\"name\":\"2\"}}";
        try (final var mapper = new JsonMapperImpl(jsonCodecs, key -> Optional.empty());
             final var reader = new StringReader(json)) {

            final var simple = mapper.read(new Types.ParameterizedTypeImpl(Map.class, String.class, Simple.class), reader);
            assertEquals("{first=Simple[name=hello], second=Simple[name=2]}", simple.toString());

            try (final var out = new StringWriter()) {
                mapper.write(simple, out);
                out.flush();
                assertEquals(json, out.toString());
            }
        }
    }

    @Test
    void listString() throws IOException {
        final var json = "[\"first\",\"second\"]";
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var reader = new StringReader(json)) {

            final var simple = mapper.read(new Types.ParameterizedTypeImpl(List.class, String.class), reader);
            assertEquals("[first, second]", simple.toString());

            try (final var out = new StringWriter()) {
                mapper.write(simple, out);
                out.flush();
                assertEquals(json, out.toString());
            }
        }
    }

    public record Simple(String name) {
    }
}
