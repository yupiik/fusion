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
package io.yupiik.fusion.json.internal.parser;

import io.yupiik.fusion.json.deserialization.AvailableCharArrayReader;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonParserTest {
    @Test
    void bigString() throws IOException {
        final var len = 5 * 1024 * 1024 + 1;
        final var w = new StringWriter();
        try (final var out = w) {
            out.write("{\"data\":\"");
            IntStream.range(0, len).forEach(i -> {
                out.write('a' + (i % 26));
            });
            out.write("\"}");
        }
        final var expected = w.toString();
        try (final var mapper = new JsonMapperImpl(List.of(), c -> Optional.empty())) {
            final var res = (Map<String, Object>) mapper.read(Object.class, new StringReader(expected));
            final var actual = res.get("data").toString();
            assertEquals(len, actual.length());
            assertEquals((char) (((len - 1) % 26 + 'a')), actual.charAt(actual.length() - 1));
        }
    }

    @Test
    void bigStringThenOtherData() throws IOException {
        final var len = 1024 * 1024 + 7;
        final var longString = IntStream.range(0, len)
                .mapToObj(i -> Character.toString('a' + (i % 26))).collect(joining()) + "$ù^*°~²~#é♨\uFE0Fjava";
        final var w = new StringWriter();
        try (final var out = w) {
            out.write("{\"first\":true,\"firstString\":\"something\",\"data\":\"");
            out.write(longString);
            out.write("\",\"number\":1234,\"otherstring\":\"simple\",");
            out.write("\"copy\":\""+longString+"\"");
            out.write('}');
        }
        final var expected = w.toString();
        try (final var mapper = new JsonMapperImpl(List.of(), c -> Optional.empty())) {
            final var res = (Map<String, Object>) mapper.read(Object.class, new StringReader(expected));
            assertEquals(longString, res.get("data"));
            assertEquals(longString, res.get("copy"));
            assertEquals(Boolean.TRUE, res.get("first"));
            assertEquals("something", res.get("firstString"));
            assertEquals(BigDecimal.valueOf(1234L), res.get("number"));
            assertEquals("simple", res.get("otherstring"));
        }
    }

    @Test
    void nullValue() {
        Stream.of(true, false).forEach(b -> {
            try (final var reader = parser("null", b)) {
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.VALUE_NULL, reader.next());
                assertFalse(reader.hasNext());
            }
        });
    }

    @Test
    void trueValue() {
        try (final var reader = parser("true")) {
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.VALUE_TRUE, reader.next());
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void falseValue() {
        try (final var reader = parser("false")) {
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.VALUE_FALSE, reader.next());
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void intValue() {
        try (final var reader = parser("123")) {
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.VALUE_NUMBER, reader.next());
            assertEquals(123, reader.getInt());
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void doubleValue() {
        try (final var reader = parser("123.56")) {
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.VALUE_NUMBER, reader.next());
            assertEquals(123.56, reader.getDouble());
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void stringValue() {
        Stream.of(true, false).forEach(b -> {
            try (final var reader = parser("\"hello\"", b)) {
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.VALUE_STRING, reader.next());
                assertEquals("hello", reader.getString());
                assertFalse(reader.hasNext());
            }
        });
    }

    @Test
    void stringUnescapedValue() {
        try (final var reader = parser("\"h\\\\ello\"")) {
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.VALUE_STRING, reader.next());
            assertEquals("h\\ello", reader.getString());
            assertFalse(reader.hasNext());
        }
    }

    @Test
    void stringUnicode() {
        Stream.of(true, false).forEach(b -> {
            try (final var reader = parser("\"\\u0039\"", b)) {
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.VALUE_STRING, reader.next());
                assertEquals("9", reader.getString());
                assertFalse(reader.hasNext());
            }
        });
    }

    @Test
    void objectEmpty() {
        try (final var reader = parser("{}")) {
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.START_OBJECT, reader.next());
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.END_OBJECT, reader.next());
            assertFalse(reader.hasNext(), () -> reader.next().name());
        }
    }

    @Test
    void object() {
        Stream.of(true, false).forEach(b -> {
            try (final var reader = parser("{\"test\":\"foo\",\"othero\":{\"something\":true},\"otherl\":[1]}", b)) {
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.START_OBJECT, reader.next());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.KEY_NAME, reader.next());
                assertEquals("test", reader.getString());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.VALUE_STRING, reader.next());
                assertEquals("foo", reader.getString());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.KEY_NAME, reader.next());
                assertEquals("othero", reader.getString());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.START_OBJECT, reader.next());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.KEY_NAME, reader.next());
                assertEquals("something", reader.getString());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.VALUE_TRUE, reader.next());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.END_OBJECT, reader.next());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.KEY_NAME, reader.next());
                assertEquals("otherl", reader.getString());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.START_ARRAY, reader.next());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.VALUE_NUMBER, reader.next());
                assertEquals(1, reader.getInt());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.END_ARRAY, reader.next());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.END_OBJECT, reader.next());
                assertFalse(reader.hasNext(), () -> reader.next().name());
            }
        });
    }

    @Test
    void listEmpty() {
        try (final var reader = parser("[]")) {
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.START_ARRAY, reader.next());
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.END_ARRAY, reader.next());
            assertFalse(reader.hasNext(), () -> reader.next().name());
        }
    }

    @Test
    void listString() {
        Stream.of(true, false).forEach(b -> {
            try (final var reader = parser("[\"hello\",\"yes\"]", b)) {
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.START_ARRAY, reader.next());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.VALUE_STRING, reader.next());
                assertEquals("hello", reader.getString());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.VALUE_STRING, reader.next());
                assertEquals("yes", reader.getString());
                assertTrue(reader.hasNext());
                assertEquals(JsonParser.Event.END_ARRAY, reader.next());
                assertFalse(reader.hasNext(), () -> reader.next().name());
            }
        });
    }

    private JsonParser parser(final String string) {
        return parser(string, false);
    }

    private JsonParser parser(final String string, final boolean provided) {
        return new JsonParser(
                provided ? new AvailableCharArrayReader(string.toCharArray()) : new StringReader(string),
                16, new BufferProvider(16, -1), true);
    }
}
