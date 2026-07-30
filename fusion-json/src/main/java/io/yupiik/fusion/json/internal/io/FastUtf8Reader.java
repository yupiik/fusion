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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

// streaming UTF-8 decoder optimized for JSON: the structure of a JSON document is always ASCII so the
// hot loop is a plain byte to char inflate, multi-byte sequences are decoded manually (surrogate pairs
// included) and sequences straddling a refill boundary are compacted to the buffer start.
// Malformed input is replaced by U+FFFD like an InputStreamReader configured with REPLACE (the default).
public class FastUtf8Reader extends Reader {
    private static final char REPLACEMENT = '\uFFFD';

    private final InputStream input;
    private final ByteBufferProvider bufferProvider;
    private byte[] buffer;
    private int position;
    private int limit;
    private boolean eof;
    private char pendingLowSurrogate;

    public FastUtf8Reader(final InputStream input, final ByteBufferProvider bufferProvider) {
        this.input = input;
        this.bufferProvider = bufferProvider;
        this.buffer = bufferProvider.newBuffer();
    }

    @Override
    public int read(final char[] cbuf, final int off, final int len) throws IOException {
        if (buffer == null) {
            throw new IOException("Stream closed");
        }
        if (len == 0) {
            return 0;
        }

        int out = off;
        final int end = off + len;
        if (pendingLowSurrogate != 0) { // the pair did not fit the previous read
            cbuf[out++] = pendingLowSurrogate;
            pendingLowSurrogate = 0;
        }
        while (out < end) {
            if (position == limit) {
                if (eof) {
                    break;
                }
                fill();
                continue;
            }
            byte b = buffer[position];
            if (b >= 0) { // ASCII fast loop
                cbuf[out++] = (char) b;
                position++;
                while (out < end && position < limit && (b = buffer[position]) >= 0) {
                    cbuf[out++] = (char) b;
                    position++;
                }
                continue;
            }

            final int lead = b & 0xFF;
            final int needed = lead < 0xC2 || lead > 0xF4 ? -1 : (lead < 0xE0 ? 2 : (lead < 0xF0 ? 3 : 4));
            if (needed < 0) { // continuation byte as lead, overlong 0xC0/0xC1 or > 0xF4
                cbuf[out++] = REPLACEMENT;
                position++;
                continue;
            }
            if (limit - position < needed) {
                if (!eof) {
                    fill(); // compacts the pending bytes at the buffer start
                    continue;
                }
                // truncated trailing sequence
                cbuf[out++] = REPLACEMENT;
                position = limit;
                continue;
            }

            final int codePoint = decode(lead, needed);
            if (codePoint < 0) {
                cbuf[out++] = REPLACEMENT;
                position++; // resync on the next byte
                continue;
            }
            position += needed;
            if (codePoint <= 0xFFFF) {
                cbuf[out++] = (char) codePoint;
            } else {
                cbuf[out++] = Character.highSurrogate(codePoint);
                if (out < end) {
                    cbuf[out++] = Character.lowSurrogate(codePoint);
                } else {
                    pendingLowSurrogate = Character.lowSurrogate(codePoint);
                }
            }
        }
        return out == off ? -1 : out - off;
    }

    // decodes needed bytes at position, returns the code point or -1 when the sequence is malformed
    private int decode(final int lead, final int needed) {
        final int b2 = buffer[position + 1] & 0xFF;
        if ((b2 & 0xC0) != 0x80) {
            return -1;
        }
        if (needed == 2) {
            return ((lead & 0x1F) << 6) | (b2 & 0x3F);
        }
        final int b3 = buffer[position + 2] & 0xFF;
        if ((b3 & 0xC0) != 0x80) {
            return -1;
        }
        if (needed == 3) {
            if ((lead == 0xE0 && b2 < 0xA0) /* overlong */ || (lead == 0xED && b2 > 0x9F) /* surrogate range */) {
                return -1;
            }
            return ((lead & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
        }
        final int b4 = buffer[position + 3] & 0xFF;
        if ((b4 & 0xC0) != 0x80
                || (lead == 0xF0 && b2 < 0x90) /* overlong */
                || (lead == 0xF4 && b2 > 0x8F) /* > U+10FFFF */) {
            return -1;
        }
        return ((lead & 0x07) << 18) | ((b2 & 0x3F) << 12) | ((b3 & 0x3F) << 6) | (b4 & 0x3F);
    }

    private void fill() throws IOException {
        final int remaining = limit - position;
        if (remaining > 0) {
            System.arraycopy(buffer, position, buffer, 0, remaining);
        }
        position = 0;
        limit = remaining;
        final int read = input.read(buffer, limit, buffer.length - limit);
        if (read < 0) {
            eof = true;
        } else {
            limit += read;
        }
    }

    @Override
    public void close() throws IOException {
        if (buffer != null) {
            bufferProvider.release(buffer);
            buffer = null;
        }
        input.close();
    }
}
