package io.yupiik.fusion.http.server.api;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

public interface Request extends Unwrappable {
    String scheme();

    String method();

    String path();

    String query();

    Flow.Publisher<ByteBuffer> body();

    Stream<Cookie> cookies();

    <T> T attribute(String key, Class<T> type);

    <T> void setAttribute(String key, T value);
}
