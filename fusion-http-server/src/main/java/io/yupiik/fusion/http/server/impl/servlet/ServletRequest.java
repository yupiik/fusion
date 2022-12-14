package io.yupiik.fusion.http.server.impl.servlet;

import jakarta.servlet.http.HttpServletRequest;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.impl.flow.ServletInputStreamSubscription;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

public class ServletRequest implements Request {
    private final HttpServletRequest delegate;
    private String uri;
    private List<Cookie> cookies;

    public ServletRequest(final HttpServletRequest delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T unwrap(final Class<T> type) {
        if (type == HttpServletRequest.class || type == jakarta.servlet.ServletRequest.class) {
            return type.cast(delegate);
        }
        return Request.super.unwrap(type);
    }

    @Override
    public String scheme() {
        return delegate.getScheme();
    }

    @Override
    public String method() {
        return delegate.getMethod();
    }

    @Override
    public String path() {
        if (uri == null) {
            uri = delegate.getRequestURI().substring(delegate.getContextPath().length());
        }
        return uri;
    }

    @Override
    public String query() {
        return delegate.getQueryString();
    }

    @Override
    public Flow.Publisher<ByteBuffer> body() {
        // not yet the best reactive impl but likely as good as servlet
        return subscriber -> {
            try {
                subscriber.onSubscribe(new ServletInputStreamSubscription(delegate.getInputStream(), subscriber));
            } catch (final IOException e) {
                subscriber.onError(e);
            }
        };
    }

    @Override
    public Stream<Cookie> cookies() {
        if (cookies == null) {
            cookies = Stream.ofNullable(delegate.getCookies())
                    .flatMap(Stream::of)
                    .<Cookie>map(ServletCookie::new)
                    .toList();
        }
        return cookies.stream();
    }

    @Override
    public <T> T attribute(final String key, final Class<T> type) {
        return type.cast(delegate.getAttribute(key));
    }

    @Override
    public <T> void setAttribute(final String key, final T value) {
        delegate.setAttribute(key, value);
    }
}
