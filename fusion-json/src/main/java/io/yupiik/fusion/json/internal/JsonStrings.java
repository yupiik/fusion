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
package io.yupiik.fusion.json.internal;

import io.yupiik.fusion.json.serialization.ExtendedWriter;

import java.io.IOException;
import java.nio.CharBuffer;

public final class JsonStrings {
    private final static char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private JsonStrings() {
        // no-op
    }

    // zero allocation fast path when nothing needs escaping (the common case), else delegates to escapeChars
    public static void escapeCharsTo(final CharSequence value, final ExtendedWriter writer) throws IOException {
        final int length = value.length();
        for (int i = 0; i < length; i++) {
            if (!isPassthrough(value.charAt(i))) {
                writer.write(escapeChars(value));
                return;
            }
        }
        writer.write('"');
        writer.write(value);
        writer.write('"');
    }

    public static CharBuffer escapeChars(final CharSequence value) {
        final int length = value.length();
        int extra = 0;
        for (int i = 0; i < length; i++) {
            final char c = value.charAt(i);
            if (!isPassthrough(c)) {
                extra += switch (c) {
                    case '"', '\\', '\b', '\f', '\n', '\r', '\t' -> 1;
                    default -> 5; // unicode escape takes 6 chars for 1
                };
            }
        }

        // count first then allocate the exact needed size once, most of the time there is no escaping at all
        final var out = new char[length + extra + 2];
        out[0] = '"';
        out[out.length - 1] = '"';
        if (extra == 0) { // fast path, bulk copy
            if (value instanceof String s) {
                s.getChars(0, length, out, 1);
            } else if (value instanceof CharBuffer cb && cb.hasArray()) {
                System.arraycopy(cb.array(), cb.arrayOffset() + cb.position(), out, 1, length);
            } else {
                for (int i = 0; i < length; i++) {
                    out[i + 1] = value.charAt(i);
                }
            }
            return CharBuffer.wrap(out);
        }

        int idx = 1;
        for (int i = 0; i < length; i++) {
            final char c = value.charAt(i);
            if (isPassthrough(c)) {
                out[idx++] = c;
                continue;
            }

            out[idx++] = '\\';
            switch (c) {
                case '"' -> out[idx++] = '"';
                case '\\' -> out[idx++] = '\\';
                case '\b' -> out[idx++] = 'b';
                case '\f' -> out[idx++] = 'f';
                case '\n' -> out[idx++] = 'n';
                case '\r' -> out[idx++] = 'r';
                case '\t' -> out[idx++] = 't';
                default -> {
                    out[idx++] = 'u';
                    out[idx++] = '0';
                    out[idx++] = '0';
                    out[idx++] = HEX_CHARS[c >> 4];
                    out[idx++] = HEX_CHARS[c & 0xF];
                }
            }
        }
        return CharBuffer.wrap(out);
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
