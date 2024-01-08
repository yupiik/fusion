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
package io.yupiik.fusion.json.serialization;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;

public class ExtendedWriter extends Writer {
    private final Writer writer;

    public ExtendedWriter(final Writer writer) {
        this.writer = writer;
    }

    public void write(final CharSequence s) throws IOException {
        if (s instanceof CharBuffer cb) { // assume it is properly flipped
            writer.write(cb.array(), cb.position(), cb.limit());
        } else {
            writer.write(s.toString());
        }
    }

    @Override
    public void write(final int c) throws IOException {
        writer.write(c);
    }

    @Override
    public void write(final char[] cbuf) throws IOException {
        writer.write(cbuf);
    }

    @Override
    public void write(final char[] cbuf, final int off, final int len) throws IOException {
        writer.write(cbuf, off, len);
    }

    @Override
    public void write(final String str) throws IOException {
        writer.write(str);
    }

    @Override
    public void write(final String str, final int off, final int len) throws IOException {
        writer.write(str, off, len);
    }

    @Override
    public Writer append(final CharSequence csq) throws IOException {
        return writer.append(csq);
    }

    @Override
    public Writer append(final CharSequence csq, final int start, final int end) throws IOException {
        return writer.append(csq, start, end);
    }

    @Override
    public Writer append(final char c) throws IOException {
        return writer.append(c);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    @Override
    public String toString() {
        return writer.toString();
    }
}
