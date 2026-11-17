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

import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.deserialization.AvailableCharArrayReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static io.yupiik.fusion.json.spi.Parser.Event.START_ARRAY;
import static io.yupiik.fusion.json.spi.Parser.Event.START_OBJECT;
import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_NUMBER;
import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonParserDocumentTest {
    @Test
    void emptyAndWhitespaceOnly() {
        for (final var input : List.of("", " ", "\t\r\n ")) {
            for (int chunk = 0; chunk <= 5; chunk++) {
                try (final var parser = parser(input, chunk)) {
                    assertFalse(parser.hasNext());
                    assertFalse(parser.hasNext());
                    assertThrows(NoSuchElementException.class, parser::next);
                }
            }
        }
    }

    @Test
    void completeDocumentsAcrossRefills() {
        for (final var input : List.of("{}", "[]", "[1, true, false, null, \"text\"]",
                "{\"key\":[{},[],{\"other\":-12.5e+2}]}")) {
            for (int chunk = 0; chunk <= 9; chunk++) {
                try (final var parser = parser(input + " \t\r\n", chunk)) {
                    drain(parser);
                    assertFalse(parser.hasNext());
                    assertThrows(NoSuchElementException.class, parser::next);
                }
            }
        }
    }

    @Test
    void rejectsRawNulInEveryLexicalPath() {
        for (final var input : List.of("\0", " \0", "\0 ", "1\0", "-12.3e+4\0", "1 \0",
                "true\0", "false \0", "null\0", "tr\0ue", "fal\0se", "nu\0ll",
                "\"\0\"", "\"abc\0def\"", "\"\\\0\"", "\"\\u00\000\"",
                "{}\0", "[] \0", "[1,\0]", "{\"key\":\0}")) {
            rejects(input);
        }
    }

    @Test
    void rejectsNonJsonWhitespaceAndSuffixGarbage() {
        for (final var whitespace : List.of("\f", "\u000b", "\u0085", "\u00a0", "\u2003")) {
            rejects(whitespace);
            rejects("{}" + whitespace);
            rejects("1" + whitespace);
            rejects("[true," + whitespace + "false]");
        }
        for (final var prefix : List.of("{}", "[]", "true", "false", "null", "\"text\"", "1")) {
            for (int padding = 0; padding <= 9; padding++) {
                rejects(prefix + " ".repeat(padding) + "x");
            }
        }
    }

    @Test
    void rejectsIncompleteAndMalformedDocuments() {
        for (final var input : List.of("{", "[", "[ ", "{\"a\"", "{\"a\":", "{\"a\":1", "[1,", "[1",
                "{\"a\":[{}", "[{}", "[1,]", "{\"a\":1,}", "[}", "{]", "[1 2]", "{\"a\" 1}",
                "tru", "fals", "nul", "-", "1.", "1e", "1e+", "01", "-01",
                "\"", "\"abc", "\"\\", "\"\\u", "\"\\u000", "\"\\u00xz\"", "\"\\q\"")) {
            rejects(input);
        }
    }

    @Test
    void skippedSubtreesMustBeComplete() {
        for (int chunk = 0; chunk <= 5; chunk++) {
            try (final var parser = parser("{\"a\":[{\"b\":1}", chunk)) {
                assertEquals(START_OBJECT, parser.next());
                assertThrows(JsonException.class, parser::skipObject);
            }
            try (final var parser = parser("[{\"a\":[1,2]}", chunk)) {
                assertEquals(START_ARRAY, parser.next());
                assertThrows(JsonException.class, parser::skipArray);
            }
            try (final var parser = parser("[{},[true],{\"a\":null}]", chunk)) {
                assertEquals(START_ARRAY, parser.next());
                parser.skipArray();
                assertFalse(parser.hasNext());
            }
        }
    }

    @Test
    void structureContextsRetainEnclosingObjectsAndArrays() {
        for (int chunk = 0; chunk <= 9; chunk++) {
            try (final var parser = parser("[{\"key\":[{}]}]", chunk)) {
                final var expected = List.of("true:false", "true:true", "true:true", "true:true",
                        "true:true", "true:true", "true:true", "true:false", "false:false");
                final var actual = new ArrayList<String>();
                assertFalse(parser.isInArray());
                assertFalse(parser.isInObject());
                while (parser.hasNext()) {
                    parser.next();
                    actual.add(parser.isInArray() + ":" + parser.isInObject());
                }
                assertEquals(expected, actual, "chunk=" + chunk);
            }
        }
    }

    @Test
    void valuesSurviveSeparatorsAndRefills() {
        final var json = "{\"longlonglongkey\":[\"longlonglongvalue\",\"\",1234567890123456789,\"tail\"],\"next\":\"end\"}";
        final var expected = List.of("START_OBJECT", "KEY_NAME:longlonglongkey", "START_ARRAY",
                "VALUE_STRING:longlonglongvalue", "VALUE_STRING:", "VALUE_NUMBER:1234567890123456789",
                "VALUE_STRING:tail", "END_ARRAY", "KEY_NAME:next", "VALUE_STRING:end", "END_OBJECT");
        for (int chunk = 0; chunk <= 9; chunk++) {
            try (final var parser = parser(json, chunk)) {
                final var actual = new ArrayList<String>();
                while (parser.hasNext()) {
                    final var event = parser.next();
                    actual.add(switch (event) {
                        case KEY_NAME, VALUE_STRING, VALUE_NUMBER -> event + ":" + parser.getString();
                        default -> event.toString();
                    });
                }
                assertEquals(expected, actual, "chunk=" + chunk);
            }
        }
    }

    @Test
    void directNextRejectsMalformedValuesAcrossRefills() {
        for (final var input : List.of("[", "[ ", "[1,", "[1,,2]", "[1:2]", "[1,\0]",
                "[1\0]", "[tr\0ue]", "[\"a\0b\"]", "[\"\\\0\"]", "[\"\\u00\000\"]",
                "{", "{\"a\":", "{\"a\"::1}", "{\"a\":\0}", "{\"a\":true,")) {
            for (int chunk = 0; chunk <= 9; chunk++) {
                try (final var parser = parser(input, chunk)) {
                    assertThrows(JsonException.class, () -> {
                        while (true) {
                            parser.next();
                        }
                    }, "input=" + input + ", chunk=" + chunk);
                }
            }
        }
    }

    @Test
    void escapedNulAndValuesSurviveLookahead() {
        for (final int chunk : new int[]{0, 1, 2, 3, 8}) {
            for (final var suffix : List.of("", " ", " \t\r\n")) {
                try (final var parser = parser("\"\"" + suffix, chunk)) {
                    assertEquals(VALUE_STRING, parser.next());
                    assertFalse(parser.hasNext());
                    assertFalse(parser.hasNext());
                    assertEquals("", parser.getString());
                    assertEquals("", parser.getChars().toString());
                }
                try (final var parser = parser("\"\"" + suffix, chunk)) {
                    assertEquals(VALUE_STRING, parser.next());
                    assertFalse(parser.hasNext());
                    assertEquals("", parser.getChars().toString());
                    assertEquals("", parser.getString());
                }
            }
        }
        for (int chunk = 0; chunk <= 9; chunk++) {
            for (final var suffix : List.of("", " ", "          ")) {
                try (final var parser = parser("\"a\\u0000b\\ntext\"" + suffix, chunk)) {
                    assertEquals(VALUE_STRING, parser.next());
                    assertFalse(parser.hasNext());
                    assertFalse(parser.hasNext());
                    assertEquals("a\0b\ntext", parser.getString());
                    assertEquals("a\0b\ntext", parser.getChars().toString());
                }
                for (final var number : List.of("0", "9", "-1", "123456789", "-12.345e+2")) {
                    try (final var parser = parser(number + suffix, chunk)) {
                        assertEquals(VALUE_NUMBER, parser.next());
                        assertFalse(parser.hasNext());
                        assertFalse(parser.hasNext());
                        assertEquals(number, parser.getString());
                        assertEquals(new BigDecimal(number), parser.getBigDecimal());
                    }
                }
            }
        }
    }

    @Test
    void bufferedLookaheadRetainsTruncationAndTokenAccess() {
        for (int chunk = 0; chunk <= 9; chunk++) {
            try (final var parser = parser("[\"value\",1]", chunk)) {
                assertEquals(START_ARRAY, parser.next());
                assertTrue(parser.hasNext());
                assertTrue(parser.hasNext());
                assertEquals(VALUE_STRING, parser.next());
                assertTrue(parser.hasNext());
                assertTrue(parser.hasNext());
                assertEquals("value", parser.getString());
                assertEquals(VALUE_NUMBER, parser.next());
                assertTrue(parser.hasNext());
                assertEquals(BigDecimal.ONE, parser.getBigDecimal());
                parser.next();
                assertFalse(parser.hasNext());
            }
            for (final var json : List.of("[", "[ \t\r\n")) {
                try (final var parser = parser(json, chunk)) {
                    assertEquals(START_ARRAY, parser.next());
                    assertThrows(JsonException.class, parser::hasNext);
                }
            }
        }
    }

    @Test
    void documentCompletionRequiresConsumedAndUnreplayedRoot() {
        for (int chunk = 0; chunk <= 9; chunk++) {
            try (final var parser = parser("{}", chunk).forDocument()) {
                assertThrows(JsonException.class, () -> parser.ensureDocument(false));
                assertEquals(START_OBJECT, parser.next());
                assertThrows(JsonException.class, () -> parser.ensureDocument(false));
                final var end = parser.next();
                parser.ensureDocument(true);
                parser.rewind(end);
                assertThrows(JsonException.class, () -> parser.ensureDocument(false));
                assertEquals(end, parser.next());
                parser.ensureDocument(true);
            }
            try (final var parser = parser("true false", chunk).forDocument()) {
                parser.next();
                parser.ensureDocument(false);
                assertThrows(JsonException.class, () -> parser.ensureDocument(true));
                assertThrows(JsonException.class, parser::next);
            }
        }
    }

    @Test
    void scalarStreaming() {
        for (int chunk = 0; chunk <= 9; chunk++) {
            try (final var parser = parser("true false null \"text\" 12 -3.5", chunk)) {
                assertEquals(6, drain(parser));
            }
        }
    }

    @Test
    void ioAndNumericErrorsAreNotJsonSyntaxErrors() {
        final var failure = new IOException("read failure");
        try (final var parser = new JsonParser(new Reader() {
            @Override
            public int read(final char[] target, final int offset, final int length) throws IOException {
                throw failure;
            }

            @Override
            public void close() {
                // no-op
            }
        }, 8, new BufferProvider(8, -1), true)) {
            final var error = assertThrows(IllegalStateException.class, parser::hasNext);
            assertFalse(error instanceof JsonException);
            assertSame(failure, error.getCause());
        }
        try (final var parser = parser("1e2000", 1)) {
            assertEquals(VALUE_NUMBER, parser.next());
            assertThrows(ArithmeticException.class, parser::getBigDecimal);
        }
        try (final var parser = parser("1e99999999999999999999", 1)) {
            assertEquals(VALUE_NUMBER, parser.next());
            assertThrows(NumberFormatException.class, parser::getBigDecimal);
        }
    }

    private void rejects(final String input) {
        for (int chunk = 0; chunk <= 9; chunk++) {
            try (final var parser = parser(input, chunk)) {
                assertThrows(JsonException.class, () -> drain(parser), "chunk=" + chunk + ", input=" + input);
            }
        }
    }

    private int drain(final JsonParser parser) {
        int events = 0;
        while (parser.hasNext()) {
            assertTrue(parser.hasNext());
            parser.next();
            events++;
        }
        return events;
    }

    private JsonParser parser(final String input, final int chunk) {
        final Reader reader = chunk == 0 ? new AvailableCharArrayReader(input.toCharArray()) : new Reader() {
            private int position;

            @Override
            public int read(final char[] target, final int offset, final int length) {
                if (position == input.length()) {
                    return -1;
                }
                final int count = Math.min(Math.min(chunk, length), input.length() - position);
                input.getChars(position, position + count, target, offset);
                position += count;
                return count;
            }

            @Override
            public void close() {
                // no-op
            }
        };
        return new JsonParser(reader, 8, new BufferProvider(8, -1), true);
    }
}
