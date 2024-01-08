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
package io.yupiik.fusion.http.server.impl.servlet;

import io.yupiik.fusion.http.server.api.HttpException;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.impl.flow.WriterPublisher;
import io.yupiik.fusion.http.server.impl.io.CloseOnceWriter;
import io.yupiik.fusion.http.server.spi.BaseEndpoint;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.SEVERE;

public class FusionServlet extends HttpServlet {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final List<? extends BaseEndpoint> endpoints;

    public FusionServlet(final List<? extends BaseEndpoint> endpoints) {
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
        final Runnable task = () -> execute(resp, request, matched.orElseThrow(), asyncContext);
        doExecute(asyncContext, task);
    }

    protected void doExecute(final AsyncContext asyncContext, final Runnable task) {
        asyncContext.start(task);
    }

    protected void execute(final HttpServletResponse resp, final Request request,
                           final BaseEndpoint matched, final AsyncContext asyncContext) {
        try {
            matched.handle(request).whenComplete((response, ex) -> {
                try {
                    if (ex != null) {
                        onError(resp, ex);
                    } else {
                        writeResponse(resp, response);
                    }
                } catch (final RuntimeException re) {
                    if (!resp.isCommitted()) {
                        onError(resp, re);
                    } else {
                        logger.log(SEVERE, re, re::getMessage);
                    }
                    throw re;
                } finally {
                    asyncContext.complete();
                }
            });
        } catch (final RuntimeException re) {
            try {
                onError(resp, re);
            } finally {
                asyncContext.complete();
            }
        }
    }

    private void onError(final HttpServletResponse resp, final Throwable ex) {
        logger.log(SEVERE, ex, ex::getMessage);
        if (unwrap(ex) instanceof HttpException he) {
            writeResponse(resp, he.getResponse());
        } else {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Throwable unwrap(final Throwable ex) {
        if (ex instanceof CompletionException) {
            return ex.getCause();
        }
        return ex;
    }

    protected void writeResponse(final HttpServletResponse resp, final Response response) {
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
                if (body instanceof WriterPublisher wp) { // optimize this path
                    try (final var writer = new CloseOnceWriter(resp.getWriter())) {
                        wp.getDelegate().accept(writer);
                    }
                } else {
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
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
