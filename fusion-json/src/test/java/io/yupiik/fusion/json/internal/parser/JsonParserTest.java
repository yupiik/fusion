/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonParserTest {
    @Test
    void nullValue() {
        try (final var reader = parser("null")) {
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.VALUE_NULL, reader.next());
            assertFalse(reader.hasNext());
        }
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
        try (final var reader = parser("\"hello\"")) {
            assertTrue(reader.hasNext());
            assertEquals(JsonParser.Event.VALUE_STRING, reader.next());
            assertEquals("hello", reader.getString());
            assertFalse(reader.hasNext());
        }
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
        try (final var reader = parser("{\"test\":\"foo\",\"othero\":{\"something\":true},\"otherl\":[1]}")) {
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
        try (final var reader = parser("[\"hello\",\"yes\"]")) {
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
    }

    private JsonParser parser(final String string) {
        return new JsonParser(new StringReader(string), 16, new BufferProvider(16), true);
    }
}
