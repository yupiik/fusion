package io.yupiik.fusion.http.server.impl;

import java.nio.ByteBuffer;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ByteBuffers {
    private ByteBuffers() {
        // no-op
    }

    public static String asString(final List<ByteBuffer> bytes) {
        final var res = new byte[bytes.stream().mapToInt(ByteBuffer::remaining).sum()];
        int start = 0;
        for (final var array : bytes) {
            final int remaining = array.remaining();
            array.get(res, start, remaining);
            start += remaining;
        }
        return new String(res, UTF_8);
    }
}