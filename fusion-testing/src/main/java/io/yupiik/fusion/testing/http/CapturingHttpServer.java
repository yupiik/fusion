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
package io.yupiik.fusion.testing.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static java.util.Optional.ofNullable;
import static java.util.logging.Level.SEVERE;
import static java.util.stream.Collectors.toMap;

public class CapturingHttpServer implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(CapturingHttpServer.class.getName());
    private static final Lock RANDOM_PORT_LOCK = new ReentrantLock();

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public CapturingHttpServer(final Consumer<HighLevelApi> spec) {
        this(toHandler(spec));
    }

    private CapturingHttpServer(final ResponseHandler handler) {
        this(new CapturingHttpServerConfiguration().handler(e -> wrapIoConsumer(handler, e)));
    }

    public CapturingHttpServer(final CapturingHttpServerConfiguration configuration) {
        try {
            RANDOM_PORT_LOCK.lock();
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 128);
            server.createContext("/").setHandler(ex -> {
                if (configuration.handler().test(ex)) {
                    return;
                }

                try (final var inputStream = ofNullable(ex.getRequestBody()).orElseGet(InputStream::nullInputStream)) {
                    final var req = new Request(
                            ex.getRequestMethod(),
                            ex.getRequestURI(),
                            ex.getRequestHeaders().entrySet().stream().collect(toMap(Map.Entry::getKey, Map.Entry::getValue)),
                            new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    );
                    requests.add(req);
                }
            });
            server.start();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        } finally {
            RANDOM_PORT_LOCK.unlock();
        }
    }

    public String base() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public List<Request> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static ResponseHandler toHandler(final Consumer<HighLevelApi> spec) {
        final var definitions = new HashMap<String, Map<String, ResponseHandler>>();
        spec.accept(new HighLevelApi() {
            @Override
            public void handle(final String method, final String path, final ResponseHandler handler) {
                definitions.computeIfAbsent(method, m -> new HashMap<>()).computeIfAbsent(path, p -> handler);
            }

            @Override
            public void map(final String method, final String path, final Consumer<ResponseSender> sender) {
                handle(method, path, (rs, ex) -> sender.accept(rs));
            }
        });
        return (rs, ex) -> {
            final var list = definitions.get(ex.getRequestMethod());
            if (list == null) {
                LOGGER.warning(() -> "'" + ex.getRequestURI() + "' not handled");
                ex.sendResponseHeaders(404, 0);
                return;
            }

            var byPath = list.get(ex.getRequestURI().getPath() +
                    (ex.getRequestURI().getRawQuery() == null || ex.getRequestURI().getRawQuery().isBlank() ? "" : ("?" + ex.getRequestURI().getRawQuery())));
            if (byPath == null) {
                byPath = list.get(ex.getRequestURI().getPath());
            }
            if (byPath == null) {
                LOGGER.warning(() -> "'" + ex.getRequestURI() + "' not handled");
                ex.sendResponseHeaders(404, 0);
                return;
            }
            byPath.accept(rs, ex);
        };
    }

    private static boolean wrapIoConsumer(final ResponseHandler handler, final HttpExchange e) {
        try {
            handler.accept(
                    (status, headers, body) -> {
                        final var bytes = body.publisher();
                        headers.forEach(e.getResponseHeaders()::add);
                        try {
                            final long responseLength = bytes.contentLength();
                            e.sendResponseHeaders(status, Math.max(0, responseLength));
                            bytes.subscribe(new Flow.Subscriber<>() {
                                private Flow.Subscription subscription;

                                @Override
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    this.subscription = subscription;
                                    subscription.request(1);
                                }

                                @Override
                                public void onNext(final ByteBuffer item) {
                                    if (item.remaining() == 0) {
                                        subscription.request(1);
                                        return;
                                    }
                                    final var buffer = new byte[item.remaining()];
                                    item.get(buffer);
                                    try {
                                        e.getResponseBody().write(buffer);
                                        e.getResponseBody().flush();
                                    } catch (final IOException ex) {
                                        onError(ex);
                                    }
                                    subscription.request(1);
                                }

                                @Override
                                public void onError(final Throwable throwable) {
                                    LOGGER.log(SEVERE, throwable, throwable::getMessage);
                                    subscription.cancel();
                                    e.close();
                                }

                                @Override
                                public void onComplete() {
                                    body.onFlush();
                                    e.close();
                                }
                            });
                        } catch (final IOException ioe) {
                            try {
                                e.sendResponseHeaders(500, 0);
                            } catch (final IOException exc) {
                                // no-op
                            }
                            throw new IllegalStateException(ioe);
                        }
                    },
                    e);
        } catch (final IOException ex) {
            try {
                e.sendResponseHeaders(500, 0);
            } catch (final IOException exc) {
                // no-op
            }
            throw new IllegalStateException(ex);
        }
        return false;
    }

    public record Request(String method, URI uri, Map<String, List<String>> headers, String payload) {
    }

    public static class CapturingHttpServerConfiguration {
        public Predicate<HttpExchange> handler;

        public Predicate<HttpExchange> handler() {
            return handler;
        }

        public CapturingHttpServerConfiguration handler(final Predicate<HttpExchange> handler) {
            this.handler = handler;
            return this;
        }
    }

    public interface ResponseHandler {
        void accept(ResponseSender responseSender, HttpExchange data) throws IOException;
    }

    public interface HighLevelApi {
        void handle(String method, String path, ResponseHandler handler);

        void map(String method, String path, Consumer<ResponseSender> sender);
    }

    public interface ResponseSender {
        default void send(int status, Map<String, String> headers, String body) {
            send(status, headers, new ResponseBodyHandler() {
                @Override
                public String string() {
                    return body;
                }
            });
        }

        void send(int status, Map<String, String> headers, ResponseBodyHandler body);
    }

    public interface ResponseBodyHandler {
        default HttpRequest.BodyPublisher publisher() {
            return HttpRequest.BodyPublishers.ofByteArray(bytes());
        }

        default String string() {
            return "";
        }

        default byte[] bytes() {
            return string().getBytes(StandardCharsets.UTF_8);
        }

        default void onFlush() {
            // no-op
        }
    }
}
