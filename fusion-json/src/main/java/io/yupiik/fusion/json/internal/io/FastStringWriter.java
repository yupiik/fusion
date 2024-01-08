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
import java.io.Writer;

// pretty much a StringWriter but using StringBuilder instead of StringBuffer which is faster
public class FastStringWriter extends Writer {
    private final StringBuilder builder;

    public FastStringWriter(final StringBuilder builder) {
        this.builder = builder;
    }

    @Override
    public String toString() {
        return builder.toString();
    }

    @Override
    public void write(final char[] cbuf, final int off, final int len) throws IOException {
        if (len > 0) {
            builder.append(cbuf, off, len);
        }
    }

    @Override
    public void write(final int c) {
        builder.append((char) c);
    }

    @Override
    public void write(final String str) throws IOException {
        builder.append(str);
    }

    @Override
    public void write(final String str, final int off, final int len) throws IOException {
        builder.append(str, off, off + len);
    }

    @Override
    public Writer append(final CharSequence csq) throws IOException {
        builder.append(csq);
        return this;
    }

    @Override
    public Writer append(final CharSequence csq, final int start, final int end) throws IOException {
        builder.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(final char c) throws IOException {
        builder.append(c);
        return this;
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void close() throws IOException {
        // no-op
    }
}
