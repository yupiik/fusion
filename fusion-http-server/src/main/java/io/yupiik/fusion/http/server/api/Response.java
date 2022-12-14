package io.yupiik.fusion.http.server.api;

import io.yupiik.fusion.http.server.impl.FusionResponse;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

public interface Response {
    int status();

    Map<String, List<String>> headers();

    List<Cookie> cookies();

    Flow.Publisher<ByteBuffer> body();

    interface Builder {
        Builder status(int value);

        Builder header(String key, String value);

        /**
         * Add a cookie to the response, use {@code Cookie.of()} to build it.
         * @param cookie the cookie to add to the response.
         * @return this.
         */
        Builder cookie(Cookie cookie);

        Builder body(Flow.Publisher<ByteBuffer> writer);

        Builder body(String body);

        Response build();
    }

    static Response.Builder of() {
        return new FusionResponse.Builder();
    }
}
