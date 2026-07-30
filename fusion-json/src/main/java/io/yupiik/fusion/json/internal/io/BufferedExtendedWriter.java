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

import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.serialization.ExtendedWriter;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;

// coalesces the many small token writes issued by the codecs into bulk writes on the delegate,
// the buffer is pooled so end() (or close()) MUST be called to drain and release it
public class BufferedExtendedWriter extends ExtendedWriter {
    private final Writer delegate;
    private final BufferProvider bufferProvider;
    private char[] buffer;
    private int size;

    public BufferedExtendedWriter(final Writer delegate, final BufferProvider bufferProvider) {
        this.delegate = delegate;
        this.bufferProvider = bufferProvider;
        this.buffer = bufferProvider.newBuffer();
    }

    // drains and gives the pooled buffer back, does NOT flush/close the delegate
    public void end() throws IOException {
        if (buffer != null) {
            drain();
            release();
        }
    }

    // gives the pooled buffer back without draining (error path)
    public void release() {
        if (buffer != null) {
            bufferProvider.release(buffer);
            buffer = null;
        }
    }

    private void drain() throws IOException {
        if (size > 0) {
            delegate.write(buffer, 0, size);
            size = 0;
        }
    }

    @Override
    public void write(final CharSequence s) throws IOException {
        if (s instanceof CharBuffer cb && cb.hasArray()) { // assume it is properly flipped
            write(cb.array(), cb.arrayOffset() + cb.position(), cb.remaining());
        } else if (s instanceof String str) {
            write(str, 0, str.length());
        } else {
            final var str = s.toString();
            write(str, 0, str.length());
        }
    }

    @Override
    public void write(final int c) throws IOException {
        if (size == buffer.length) {
            drain();
        }
        buffer[size++] = (char) c;
    }

    @Override
    public void write(final char[] cbuf) throws IOException {
        write(cbuf, 0, cbuf.length);
    }

    @Override
    public void write(final char[] cbuf, final int off, final int len) throws IOException {
        if (len >= buffer.length) { // no point buffering, bulk write through
            drain();
            delegate.write(cbuf, off, len);
            return;
        }
        if (size + len > buffer.length) {
            drain();
        }
        System.arraycopy(cbuf, off, buffer, size, len);
        size += len;
    }

    @Override
    public void write(final String str) throws IOException {
        write(str, 0, str.length());
    }

    @Override
    public void write(final String str, final int off, final int len) throws IOException {
        if (len >= buffer.length) {
            drain();
            delegate.write(str, off, len);
            return;
        }
        if (size + len > buffer.length) {
            drain();
        }
        str.getChars(off, off + len, buffer, size);
        size += len;
    }

    @Override
    public Writer append(final CharSequence csq) throws IOException {
        write(csq == null ? "null" : csq);
        return this;
    }

    @Override
    public Writer append(final CharSequence csq, final int start, final int end) throws IOException {
        final var s = csq == null ? "null" : csq;
        write(s.subSequence(start, end).toString());
        return this;
    }

    @Override
    public Writer append(final char c) throws IOException {
        write(c);
        return this;
    }

    @Override
    public void flush() throws IOException {
        drain();
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        end();
        delegate.close();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
