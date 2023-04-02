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
package io.yupiik.fusion.json.internal;

import java.nio.CharBuffer;

public final class JsonStrings {
    private final static char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private JsonStrings() {
        // no-op
    }

    /* not yet used
    public static CharBuffer escapeCharsNoQuote(final CharSequence value) { // todo: optimize for numbers
        // no margin, often used for numbers so no escaping most of the time
        final var buffer = escapeChars(CharBuffer.allocate(value.length()), 0, 0, value);
        buffer.limit(value.length());
        buffer.position(0);
        return buffer;
    }
    */

    public static CharBuffer escapeChars(final CharSequence value) {
        var array = CharBuffer.allocate(value.length() + 4 /*2 for quotes + a few margin if there are a few escapes*/);
        array.put(0, '"');
        array = escapeChars(array, 1, 2, value);
        if (array.capacity() == array.limit()) {
            final var newArray = CharBuffer.allocate(array.capacity() + 1);
            newArray.put(array.array(), 0, array.limit());
            array = newArray;
        } else {
            array.limit(array.limit() + 1);
        }
        array.put(array.limit() - 1, '"');
        array.position(0);
        return array;
    }

    private static CharBuffer escapeChars(CharBuffer array,
                                          int idx,
                                          int capacityMargin,
                                          final CharSequence value) {
        final var length = value.length();
        for (int i = 0; i < length; i++) {
            final char c = value.charAt(i);
            if (isPassthrough(c)) {
                if (capacityMargin <= 0 && array.capacity() <= idx) {
                    capacityMargin = 2;
                    final var newArray = CharBuffer.allocate(array.capacity() + length - i + capacityMargin);
                    newArray.put(array.array(), 0, array.limit());
                    array = newArray;
                }
                array.put(idx++, c);
                continue;
            }

            switch (c) {
                case '"' -> {
                    if (capacityMargin-- <= 0) {
                        capacityMargin = 4;
                        final var newArray = CharBuffer.allocate(array.capacity() + capacityMargin);
                        newArray.put(array.array(), 0, array.limit());
                        array = newArray;
                    }
                    array.put(idx, '\\');
                    array.put(idx + 1, '"');
                    idx += 2;
                    capacityMargin--;
                }
                case '\\' -> {
                    if (capacityMargin-- <= 0) {
                        capacityMargin = 4;
                        final var newArray = CharBuffer.allocate(array.capacity() + capacityMargin);
                        newArray.put(array.array(), 0, array.limit());
                        array = newArray;
                    }
                    array.put(idx, '\\');
                    array.put(idx + 1, '\\');
                    idx += 2;
                    capacityMargin--;
                }
                case '\b' -> {
                    if (capacityMargin-- <= 0) {
                        capacityMargin = 4;
                        final var newArray = CharBuffer.allocate(array.capacity() + capacityMargin);
                        newArray.put(array.array(), 0, array.limit());
                        array = newArray;
                    }
                    array.put(idx, '\\');
                    array.put(idx + 1, 'b');
                    idx += 2;
                    capacityMargin--;
                }
                case '\f' -> {
                    if (capacityMargin-- <= 0) {
                        capacityMargin = 4;
                        final var newArray = CharBuffer.allocate(array.capacity() + capacityMargin);
                        newArray.put(array.array(), 0, array.limit());
                        array = newArray;
                    }
                    array.put(idx, '\\');
                    array.put(idx + 1, 'f');
                    idx += 2;
                    capacityMargin--;
                }
                case '\n' -> {
                    if (capacityMargin-- <= 0) {
                        capacityMargin = 4;
                        final var newArray = CharBuffer.allocate(array.capacity() + capacityMargin);
                        newArray.put(array.array(), 0, array.limit());
                        array = newArray;
                    }
                    array.put(idx, '\\');
                    array.put(idx + 1, 'n');
                    idx += 2;
                    capacityMargin--;
                }
                case '\r' -> {
                    if (capacityMargin-- <= 0) {
                        capacityMargin = 4;
                        final var newArray = CharBuffer.allocate(array.capacity() + capacityMargin);
                        newArray.put(array.array(), 0, array.limit());
                        array = newArray;
                    }
                    array.put(idx, '\\');
                    array.put(idx + 1, 'r');
                    idx += 2;
                    capacityMargin--;
                }
                case '\t' -> {
                    if (capacityMargin-- <= 0) {
                        capacityMargin = 4;
                        final var newArray = CharBuffer.allocate(array.capacity() + capacityMargin);
                        newArray.put(array.array(), 0, array.limit());
                        array = newArray;
                    }
                    array.put(idx, '\\');
                    array.put(idx + 1, 't');
                    idx += 2;
                    capacityMargin--;
                }
                default -> {
                    if (capacityMargin < 6) {
                        capacityMargin = 24; // unicode is used and unicode is 6 chars and not 2 as previous cases so make it wider
                        final var newArray = CharBuffer.allocate(array.capacity() + capacityMargin);
                        newArray.put(array.array(), 0, array.limit());
                        array = newArray;
                    }
                    array.put(idx, '\\');
                    array.put(idx + 1, 'u');
                    array.put(idx + 2, '0');
                    array.put(idx + 3, '0');
                    array.put(idx + 4, HEX_CHARS[c >> 4]);
                    array.put(idx + 5, HEX_CHARS[c & 0xF]);
                    idx += 6;
                    capacityMargin -= 6;
                }
            }
        }
        array.limit(idx);
        return array;
    }

    // important: String uses bytes and we are reader/writer (so chars) based so avoid when possible/perf are important
    // use escapeChars for anything but prettyformatter
    public static String escape(final String value) {
        final var charBuffer = escapeChars(value);
        return new String(charBuffer.array(), 0, charBuffer.limit());
    }

    public static char asEscapedChar(final char current) {
        return switch (current) {
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case '"' -> '\"';
            case '\\' -> '\\';
            case '/' -> '/';
            case '[' -> '[';
            case ']' -> ']';
            default -> {
                if (Character.isHighSurrogate(current) || Character.isLowSurrogate(current)) {
                    yield current;
                }
                throw new IllegalStateException("Invalid escape sequence '" + current + "' (Codepoint: " + String.valueOf(current).codePointAt(0));
            }
        };
    }

    private static boolean isPassthrough(final char c) {
        return c >= 0x20 && c != 0x22 && c != 0x5c;
    }
}
