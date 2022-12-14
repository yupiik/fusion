package io.yupiik.fusion.json.internal.parser;

import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferProvider {
    private final int size;
    private final ConcurrentLinkedQueue<char[]> queue = new ConcurrentLinkedQueue<>();

    public BufferProvider(final int size) {
        this.size = size;
    }

    public char[] newBuffer() {
        final var buffer = queue.poll();
        if (buffer == null) {
            return new char[size];
        }
        return buffer;
    }

    public void release(final char[] value) {
        queue.offer(value);
    }
}
