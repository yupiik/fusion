package io.yupiik.fusion.http.server.impl;

import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.IOConsumer;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.impl.flow.BytesPublisher;
import io.yupiik.fusion.http.server.impl.flow.WriterPublisher;

import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

public class FusionResponse implements Response {
    private final int status;
    private final Map<String, List<String>> headers;
    private final List<Cookie> cookies;
    private final Flow.Publisher<ByteBuffer> body;

    private FusionResponse(final int status, final Map<String, List<String>> headers, final List<Cookie> cookies,
                           final Flow.Publisher<ByteBuffer> body) {
        this.status = status;
        this.headers = headers;
        this.cookies = cookies;
        this.body = body;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public Map<String, List<String>> headers() {
        return headers == null ? Map.of() : headers;
    }

    @Override
    public List<Cookie> cookies() {
        return cookies == null ? List.of() : cookies;
    }

    @Override
    public Flow.Publisher<ByteBuffer> body() {
        return body;
    }

    public static class Builder implements Response.Builder {
        private int status = 200;
        private Map<String, List<String>> headers;
        private List<Cookie> cookies;
        private Flow.Publisher<ByteBuffer> body;

        @Override
        public Response.Builder status(final int value) {
            this.status = value;
            return this;
        }

        @Override
        public Response.Builder header(final String key, final String value) {
            if (headers == null) {
                headers = new LinkedHashMap<>();
            }
            headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            return this;
        }

        @Override
        public Response.Builder cookie(final Cookie cookie) {
            if (cookies == null) {
                cookies = new ArrayList<>();
            }
            cookies.add(cookie);
            return this;
        }

        @Override
        public Response.Builder body(final Flow.Publisher<ByteBuffer> writer) {
            this.body = writer;
            return this;
        }

        @Override
        public Response.Builder body(final String body) {
            return body(new BytesPublisher(body));
        }

        @Override
        public Response.Builder body(final IOConsumer<Writer> bodyHandler) {
            return body(new WriterPublisher(bodyHandler));
        }

        @Override
        public Response build() {
            return new FusionResponse(status, headers, cookies, body);
        }
    }
}
