package io.yupiik.fusion.http.server.impl.io;

import java.io.IOException;
import java.io.Writer;

public class CloseOnceWriter extends Writer {
    private final Writer delegate;
    private boolean closed = false;

    public CloseOnceWriter(final Writer delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(final int c) throws IOException {
        delegate.write(c);
    }

    @Override
    public void write(final char[] cbuf) throws IOException {
        delegate.write(cbuf);
    }

    @Override
    public void write(final char[] cbuf, final int off, final int len) throws IOException {
        delegate.write(cbuf, off, len);
    }

    @Override
    public void write(final String str) throws IOException {
        delegate.write(str);
    }

    @Override
    public void write(final String str, final int off, final int len) throws IOException {
        delegate.write(str, off, len);
    }

    @Override
    public Writer append(final CharSequence csq) throws IOException {
        delegate.append(csq);
        return this;
    }

    @Override
    public Writer append(final CharSequence csq, final int start, final int end) throws IOException {
        delegate.append(csq, start, end);
        return this;
    }

    @Override
    public Writer append(final char c) throws IOException {
        delegate.append(c);
        return this;
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        delegate.close();
    }
}
