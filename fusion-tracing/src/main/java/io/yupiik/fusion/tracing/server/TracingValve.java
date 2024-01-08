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
package io.yupiik.fusion.tracing.server;

import io.yupiik.fusion.tracing.collector.AccumulatingSpanCollector;
import io.yupiik.fusion.tracing.request.PendingSpan;
import io.yupiik.fusion.tracing.span.Span;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class TracingValve extends ValveBase {
    private final ServerTracingConfiguration configuration;
    private final AccumulatingSpanCollector collector;
    private final Supplier<Object> idGenerator;
    private final Clock clock;
    private final boolean closeCollector;

    public TracingValve(final ServerTracingConfiguration configuration,
                        final AccumulatingSpanCollector collector,
                        final Supplier<Object> idGenerator,
                        final Clock clock) {
        this(configuration, collector, idGenerator, clock, true);
    }

    public TracingValve(final ServerTracingConfiguration configuration,
                        final AccumulatingSpanCollector collector,
                        final Supplier<Object> idGenerator,
                        final Clock clock,
                        final boolean closeCollector) {
        super(true);
        this.configuration = requireNonNull(configuration, "configuration must be not null");
        this.collector = requireNonNull(collector, "collector must be not null");
        this.idGenerator = requireNonNull(idGenerator, "idGenerator must be not null");
        this.clock = requireNonNull(clock, "clock must be not null");
        this.closeCollector = closeCollector;
    }

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        try {
            super.stopInternal();
        } finally {
            if (closeCollector) {
                collector.close();
            }
        }
    }

    @Override
    public void invoke(final Request request, final Response response) throws IOException, ServletException {
        final var start = clock.instant();
        final var remoteAddr = request.getRemoteAddr();

        final var localEndpoint = remoteAddr.contains("::") ?
                new Span.Endpoint(configuration.getServiceName(), null, remoteAddr, request.getServerPort()) :
                new Span.Endpoint(configuration.getServiceName(), remoteAddr, null, request.getServerPort());

        final var tags = new HashMap<>(configuration.getTags());
        tags.putIfAbsent("http.url", request.getRequestURI());
        tags.putIfAbsent("http.method", request.getMethod());

        final var traceTrace = request.getHeader(configuration.getTraceHeader());
        final var spanTrace = request.getHeader(configuration.getSpanHeader());

        final var id = idGenerator.get();
        final var traceId = traceTrace != null ? traceTrace : idGenerator.get();
        final LongFunction<Span> spanFn = duration -> new Span(
                traceId, spanTrace, id, configuration.getOperation(), "SERVER",
                TimeUnit.MILLISECONDS.toMicros(start.toEpochMilli()),
                duration, localEndpoint, null, tags, null, null, null);

        request.setAttribute(PendingSpan.class.getName(), new PendingSpan(traceId, id));
        try {
            getNext().invoke(request, response);
        } catch (final RuntimeException | IOException | ServletException re) {
            addErrorTag(tags, re);
            throw re;
        } finally {
            if (request.isAsyncStarted()) {
                request.getAsyncContext().addListener(new AsyncListener() {
                    private void status(final AsyncEvent event) {
                        collectSpan(finish((HttpServletResponse) event.getSuppliedResponse(), spanFn, start));
                    }

                    @Override
                    public void onComplete(final AsyncEvent event) {
                        status(event);
                    }

                    @Override
                    public void onTimeout(final AsyncEvent event) {
                        tags.putIfAbsent("http.error", "timeout");
                        status(event);
                    }

                    @Override
                    public void onError(final AsyncEvent event) {
                        addErrorTag(tags, event.getThrowable());
                        status(event);
                    }

                    @Override
                    public void onStartAsync(final AsyncEvent event) {
                        request.getAsyncContext().addListener(this);
                    }
                });
            } else {
                collectSpan(finish(response, spanFn, start));
            }
        }
    }

    protected void addErrorTag(final Map<String, Object> tags, final Throwable throwable) {
        tags.putIfAbsent("http.error", throwable == null ?
                "unknown" :
                (throwable.getMessage() == null ? throwable.getClass().getName() : throwable.getMessage()));
    }

    protected Span finish(final HttpServletResponse response, final LongFunction<Span> spanFn, final Instant start) {
        final var end = clock.instant();
        final var span = spanFn.apply(TimeUnit.MILLISECONDS.toMicros(end.minusMillis(start.toEpochMilli()).toEpochMilli()));
        span.tags().putIfAbsent("http.status", Integer.toString(response.getStatus()));
        return span;
    }

    protected void collectSpan(final Span span) {
        collector.accept(span);
    }
}
