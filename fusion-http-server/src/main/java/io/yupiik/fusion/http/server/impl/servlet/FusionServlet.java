package io.yupiik.fusion.http.server.impl.servlet;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.spi.Endpoint;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.SEVERE;

public class FusionServlet extends HttpServlet {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final List<Endpoint> endpoints;

    public FusionServlet(final List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse resp) {
        if (endpoints.isEmpty()) {
            resp.setStatus(404);
            return;
        }

        final var request = new ServletRequest(req);
        final var matched = endpoints.stream()
                .filter(e -> e.matches(request))
                .findFirst();
        if (matched.isEmpty()) {
            resp.setStatus(404);
            return;
        }

        final var asyncContext = req.startAsync();
        // todo: custom thread pool instead of container one
        asyncContext.start(() -> execute(resp, request, matched.orElseThrow(), asyncContext));
    }

    private void execute(final HttpServletResponse resp, final Request request,
                         final Endpoint matched, final AsyncContext asyncContext) {
        matched.handle(request).whenComplete((response, ex) -> {
            try {
                if (ex != null) {
                    logger.log(SEVERE, ex, ex::getMessage);
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                } else {
                    writeResponse(resp, response);
                }
            } finally {
                asyncContext.complete();
            }
        });
    }

    private void writeResponse(final HttpServletResponse resp, final Response response) {
        resp.setStatus(response.status());
        if (!response.headers().isEmpty()) {
            response.headers().forEach((k, v) -> {
                switch (v.size()) {
                    case 0 -> {
                        // just skip
                    }
                    case 1 -> resp.setHeader(k, v.get(0));
                    default -> v.forEach(value -> resp.addHeader(k, value));
                }
            });
        }
        if (!response.cookies().isEmpty()) {
            response.cookies().forEach(cookie -> {
                try {
                    resp.addCookie(cookie.unwrap(Cookie.class));
                } catch (final IllegalArgumentException iae) { // unlikely but then just convert
                    final var impl = new jakarta.servlet.http.Cookie(
                            requireNonNull(cookie.name(), "Cookie name required"),
                            requireNonNull(cookie.value(), "Cookie value required"));
                    impl.setMaxAge(cookie.maxAge());
                    impl.setSecure(cookie.secure());
                    impl.setHttpOnly(cookie.httpOnly());
                    if (cookie.path() != null) {
                        impl.setPath(cookie.path());
                    }
                    if (cookie.domain() != null) {
                        impl.setDomain(cookie.domain());
                    }
                    resp.addCookie(impl);
                }
            });
        }
        final var body = response.body();
        if (body != null) {
            try {
                final var stream = resp.getOutputStream();
                final var channel = Channels.newChannel(stream);
                body.subscribe(new Flow.Subscriber<>() {
                    private Flow.Subscription subscription;
                    private boolean closed = false;

                    @Override
                    public void onSubscribe(final Flow.Subscription subscription) {
                        this.subscription = subscription;
                        this.subscription.request(1);
                    }

                    @Override
                    public void onNext(final ByteBuffer item) {
                        try {
                            channel.write(item);

                            stream.flush(); // todo: make it configurable? idea is to enable to support SSE or things like that easily

                            subscription.request(1);
                        } catch (final IOException e) {
                            subscription.cancel();
                            onError(e);
                        }
                    }

                    @Override
                    public void onError(final Throwable throwable) {
                        if (!resp.isCommitted()) {
                            resp.setStatus(500);
                        }
                        doClose();
                    }

                    @Override
                    public void onComplete() {
                        doClose();
                    }

                    private synchronized void doClose() {
                        if (closed) {
                            return;
                        }
                        try {
                            channel.close();
                        } catch (final IOException e) {
                            logger.log(SEVERE, e, e::getMessage);
                        }
                        closed = true;
                    }
                });
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
