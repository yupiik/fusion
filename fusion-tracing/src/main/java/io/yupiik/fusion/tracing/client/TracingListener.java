/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.tracing.client;

import io.yupiik.fusion.httpclient.core.listener.RequestListener;
import io.yupiik.fusion.httpclient.core.request.UnlockedHttpRequest;
import io.yupiik.fusion.tracing.collector.AccumulatingSpanCollector;
import io.yupiik.fusion.tracing.request.PendingSpan;
import io.yupiik.fusion.tracing.span.Span;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElseGet;

public class TracingListener implements RequestListener<TracingListener.State> {
    private final ClientTracingConfiguration configuration;
    private final AccumulatingSpanCollector collector;
    private final Supplier<Object> idGenerator;
    private final Function<HttpRequest, PendingSpan> contextAttributeEvaluator;
    private final Clock clock;
    private final Map<String, String> ips = new ConcurrentHashMap<>();

    // backward compat
    public TracingListener(final ClientTracingConfiguration configuration,
                           final AccumulatingSpanCollector collector,
                           final Supplier<Object> idGenerator,
                           final Supplier<PendingSpan> contextAttributeEvaluator,
                           final Clock clock) {
        this(configuration, collector, idGenerator, r -> contextAttributeEvaluator.get(), clock);
    }

    /**
     * @param configuration             the tracing configuration.
     * @param collector                 where to send spans to when finished.
     * @param idGenerator               how to create an identifier for a span.
     * @param contextAttributeEvaluator the way to lookup parent span.
     * @param clock                     clock to measure span duration.
     */
    public TracingListener(final ClientTracingConfiguration configuration,
                           final AccumulatingSpanCollector collector,
                           final Supplier<Object> idGenerator,
                           final Function<HttpRequest, PendingSpan> contextAttributeEvaluator,
                           final Clock clock) {
        this.configuration = requireNonNull(configuration, "configuration can't be null");
        this.collector = requireNonNull(collector, "collector can't be null");
        this.idGenerator = requireNonNull(idGenerator, "contextAttributeEvaluator can't be null");
        this.contextAttributeEvaluator = requireNonNull(contextAttributeEvaluator, "contextAttributeEvaluator can't be null");
        this.clock = requireNonNull(clock, "clock can't be null");
    }

    @Override
    public RequestListener.State<State> before(final long count, final HttpRequest request) {
        final var start = clock.instant();
        final var ip = ipOf(request.uri().getHost());

        final var tags = new HashMap<>(configuration.getTags());
        tags.putIfAbsent("http.url", request.uri().toASCIIString());
        tags.putIfAbsent("http.method", request.method());
        tags.putIfAbsent("peer.hostname", request.uri().getHost());
        tags.putIfAbsent("peer.port", Integer.toString(request.uri().getPort()));
        tags.putIfAbsent("span.kind", "CLIENT");

        final var endpoint = ip.contains("::") ?
                new Span.Endpoint(configuration.getServiceName(), null, ip, request.uri().getPort()) :
                new Span.Endpoint(configuration.getServiceName(), ip, null, request.uri().getPort());

        final var parent = contextAttributeEvaluator.apply(request);
        final var timestamp = TimeUnit.MILLISECONDS.toMicros(start.toEpochMilli());
        final var id = idGenerator.get();
        final var traceId = parent != null ? parent.traceId() : idGenerator.get();
        final var parentId = parent != null ? parent.id() : null;

        return new RequestListener.State<>(new UnlockedHttpRequest(
                request.bodyPublisher(), request.method(), request.timeout(), request.expectContinue(), request.uri(), request.version(),
                HttpHeaders.of(customizeHeaders(request, traceId, id, traceId), (a, b) -> true)),
                new State(start, duration -> new Span(
                        traceId, parentId, id, configuration.getOperation(), "CLIENT",
                        timestamp, duration, null, endpoint, tags,
                        null, null, null)));
    }

    protected String ipOf(final String host) {
        return ips.computeIfAbsent(host, h -> {
            try {
                return InetAddress.getByName(host).getHostAddress();
            } catch (final UnknownHostException uhe) {
                return host;
            }
        });
    }

    @Override
    public void after(final State state, final HttpRequest request, final Throwable error, final HttpResponse<?> response) {
        final var end = clock.instant();
        final var span = state.span.apply(TimeUnit.MILLISECONDS.toMicros(end.minusMillis(state.start.toEpochMilli()).toEpochMilli()));
        if (response != null) {
            span.tags().putIfAbsent("http.status_code", Integer.toString(response.statusCode()));
        }
        if (error != null) {
            span.tags().put("error", "true");
            span.tags().put("error.message", requireNonNullElseGet(error.getMessage(), () -> error.getClass().getName()));
        }
        collectSpan(span);
    }

    protected HashMap<String, List<String>> customizeHeaders(final HttpRequest request, final Object trace, final Object id, final Object parent) {
        final var headers = new HashMap<>(request.headers().map());
        if (trace != null) {
            headers.put(configuration.getTraceHeader(), List.of(String.valueOf(trace)));
        }
        headers.put(configuration.getSpanHeader(), List.of(String.valueOf(id)));
        if (parent != null) {
            headers.put(configuration.getParentHeader(), List.of(String.valueOf(parent)));
        }
        return headers;
    }

    protected void collectSpan(final Span span) {
        collector.accept(span);
    }

    protected static class State {
        private final Instant start;
        private final LongFunction<Span> span;

        protected State(final Instant start, final LongFunction<Span> span) {
            this.start = start;
            this.span = span;
        }
    }
}
