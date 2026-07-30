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
package io.yupiik.fusion.json.internal.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastUtf8ReaderTest {
    @Test
    void asciiOnly() throws IOException {
        assertEquals("hello \"json\" world", decode("hello \"json\" world", 16));
    }

    @Test
    void multiBytesAtEveryBoundaryOffset() throws IOException {
        // slide 2/3/4 byte sequences over a tiny buffer so every refill/compaction case is hit
        for (final var symbol : new String[]{"\u00e9", "\u4f60", "\ud83d\ude00" /* emoji, 4 utf-8 bytes */}) {
            for (int prefix = 0; prefix < 20; prefix++) {
                final var value = "a".repeat(prefix) + symbol.repeat(3) + "z".repeat(5);
                for (final int bufferSize : new int[]{8, 16, 4096}) {
                    assertEquals(value, decode(value, bufferSize), () -> value);
                }
            }
        }
    }

    @Test
    void mixedContent() throws IOException {
        final var value = "{\"name\":\"e\u00e9\u00e8 \u4f60\u597d \ud83d\ude00 end\",\"count\":1234}";
        for (final int bufferSize : new int[]{8, 16, 8192}) {
            assertEquals(value, decode(value, bufferSize));
        }
    }

    @Test
    void surrogatePairSplitAcrossReads() throws IOException {
        // force the low surrogate to be pending between two read() calls by using a 1-char output window
        final var bytes = "\ud83d\ude00".getBytes(StandardCharsets.UTF_8);
        try (final var reader = new FastUtf8Reader(new ByteArrayInputStream(bytes), new ByteBufferProvider(16, -1))) {
            final var single = new char[1];
            final var out = new StringBuilder();
            int read;
            while ((read = reader.read(single, 0, 1)) >= 0) {
                out.append(single, 0, read);
            }
            assertEquals("\ud83d\ude00", out.toString());
        }
    }

    @Test
    void malformedInputIsReplacedNotFailing() throws IOException {
        // lone continuation byte, truncated 3-bytes sequence at eof, invalid lead
        for (final var bytes : new byte[][]{
                {(byte) 0x80, 'a'},
                {'a', (byte) 0xE4, (byte) 0xBD},
                {(byte) 0xFF, 'b'},
                {(byte) 0xC0, (byte) 0xAF} /* overlong */}) {
            try (final var reader = new FastUtf8Reader(new ByteArrayInputStream(bytes), new ByteBufferProvider(16, -1))) {
                final var buffer = new char[16];
                final var out = new StringBuilder();
                int read;
                while ((read = reader.read(buffer, 0, buffer.length)) >= 0) {
                    out.append(buffer, 0, read);
                }
                assertTrue(out.toString().indexOf('\ufffd') >= 0, out::toString);
            }
        }
    }

    private String decode(final String expected, final int bufferSize) throws IOException {
        final var bytes = expected.getBytes(StandardCharsets.UTF_8);
        try (final var reader = new FastUtf8Reader(new ByteArrayInputStream(bytes), new ByteBufferProvider(bufferSize, -1))) {
            final var buffer = new char[7]; // odd output window to shake the pending surrogate handling
            final var out = new StringBuilder();
            int read;
            while ((read = reader.read(buffer, 0, buffer.length)) >= 0) {
                out.append(buffer, 0, read);
            }
            return out.toString();
        }
    }
}
