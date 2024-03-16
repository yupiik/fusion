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
package io.yupiik.fusion.tracing.opentelemetry;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.tracing.span.Span;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.groupingBy;

public class OpenTelemetryFlusher implements Consumer<Collection<Span>> {
    private final JsonMapper mapper;
    private final HttpClient client;
    private final OpenTelemetryFlusherConfiguration configuration;
    private final List<URI> urls;

    /**
     * Will use JSON format.
     *
     * @param mapper        JSON mapper to serialize the spans.
     * @param client        HTTP client to send spans.
     * @param configuration the client configuration.
     */
    public OpenTelemetryFlusher(final JsonMapper mapper,
                                final HttpClient client, // potentially an enriched one
                                final OpenTelemetryFlusherConfiguration configuration) {
        this.mapper = mapper;
        this.client = client;
        this.configuration = configuration;
        this.urls = configuration.getUrls().stream().map(URI::create).toList();
    }

    // impl note: be very cautious with exp backoff or any retry on the same url
    //            can just either lock the runtime or explode in mem so better to just throw away spans
    @Override
    public void accept(final Collection<Span> spans) {
        if (spans.isEmpty()) {
            return;
        }

        final var byService = spans.stream()
                .collect(groupingBy(s -> {
                    final var endpoint = s.localEndpoint() == null ? s.remoteEndpoint() : s.localEndpoint();
                    return endpoint == null || endpoint.serviceName() == null || endpoint.serviceName().isBlank() ? "" : endpoint.serviceName();
                }));
        final byte[] payload = mapper.toBytes(Map.of(
                "resourceSpans",
                byService.entrySet().stream()
                        .map(e -> map(
                                "resource", Map.of(
                                        "attributes", List.of(map(
                                                "key", "service.name",
                                                "value", Map.of("stringValue", e.getKey())))),
                                "scopeSpans", List.of(map(
                                        "scope", Map.of("name", "fusion-tracing"),
                                        "spans", e.getValue().stream().map(this::toJson).toList()))))
                        .toList()));

        RuntimeException error = null;
        for (final var url : urls) {
            final var base = HttpRequest.newBuilder()
                    .timeout(configuration.getTimeout())
                    .uri(url)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
            final var requestBuilder = base.header("content-type", "application/json");
            if (configuration.isGzip()) {
                requestBuilder.header("content-encoding", "gzip");
            }
            configuration.getHeaders().forEach(requestBuilder::header);
            try {
                final var response = client.send(requestBuilder.build(), ofString());
                if (response.statusCode() >= 200 && response.statusCode() <= 300) {
                    return;
                }
            } catch (final IOException | RuntimeException e) {
                if (error == null) {
                    error = new SpansException(payload);
                }
                error.addSuppressed(e);
            } catch (final InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        throw error == null ?
                new IllegalStateException("No opentelemtry url configured, either disable the collector or configure it properly") :
                error;
    }

    private byte[] toJson(final Collection<Span> spans) throws IOException {
        // todo: opt using a preallocated buffer instead of this autosizing
        final var bytes = mapper.toString(spans.stream()
                        .map(this::toJson)
                        .toList())
                .getBytes(UTF_8);

        if (configuration.isGzip()) {
            final var gzipped = new ByteArrayOutputStream();
            try (final var in = new ByteArrayInputStream(bytes);
                 final var gzipOutputStream = new GZIPOutputStream(gzipped)) {
                in.transferTo(gzipOutputStream);
            }
            return gzipped.toByteArray();
        }

        return bytes;
    }

    private Map<String, Object> toJson(final Span span) {
        final var map = new LinkedHashMap<String, Object>();
        if (span.traceId() != null) {
            final var value = span.traceId().toString();
            // must be 32 hex chars
            if (value.length() >= 32) { // > can be an issue but shouldn't occur
                map.put("traceId", value); // assume IdGenerator.HEX was used
            } else {
                map.put("traceId", "0".repeat(32 - value.length()) + value);
            }
        }
        if (span.id() != null) {
            final var value = span.id().toString();
            // must be 16 hex chars
            if (value.length() >= 16) { // > can be an issue but shouldn't occur
                map.put("spanId", value); // assume IdGenerator.HEX was used
            } else {
                map.put("spanId", "0".repeat(16 - value.length()) + value);
            }
        }
        // map.put("traceState", "");
        if (span.parentId() != null) {
            final var value = span.parentId().toString();
            if (value.length() >= 16) {
                map.put("spanId", value);
            } else {
                map.put("spanId", "0".repeat(16 - value.length()) + value);
            }
        }
        if (span.name() != null) {
            map.put("name", span.name());
        }
        if (span.kind() != null) {
            map.put("kind", switch (span.kind().toLowerCase(ROOT)) {
                case "internal" -> 1;
                case "server" -> 2;
                case "client" -> 3;
                case "producer" -> 4;
                case "consumer" -> 5;
                default -> 0;
            });
        }

        if (span.timestamp() != null) {
            map.put("startTimeUnixNano", TimeUnit.MICROSECONDS.toNanos(span.timestamp()));
            if (span.duration() != null) {
                map.put("endTimeUnixNano", TimeUnit.MICROSECONDS.toNanos(span.timestamp() + span.duration()));
            }
        }

        final List<Object> attributes = new ArrayList<>(span.tags() == null || span.tags().isEmpty() ?
                List.of() :
                span.tags().entrySet().stream()
                        .map(e -> toJson(e.getKey(), e.getValue()))
                        .toList());
        if (span.remoteEndpoint() != null) {
            addEndpoint(attributes, span.remoteEndpoint());
        } else if (span.localEndpoint() != null) {
            addEndpoint(attributes, span.localEndpoint());
        }
        map.put("attributes", attributes);

        /* not used
        map.put("droppedAttributesCount", 0);

        map.put("events", List.of());
        map.put("droppedEventsCount", 0);

        map.put("links", List.of());
        map.put("droppedLinksCount", 0);
         */

        map.put("status", Map.of("code", span.tags() != null &&
                (span.tags().containsKey("error") || span.tags().containsKey("http.error") ||
                        isHttpError(span.tags().get("http.status_code"))) ?
                2 : 1));
        // map.put("flags", 0 /* 1 means sampled, 0 default*/);

        return map;
    }

    private void addEndpoint(final List<Object> attributes, final Span.Endpoint endpoint) {
        if (endpoint.serviceName() != null) {
            attributes.add(map(
                    "key", "serviceName",
                    "value", Map.of(
                            "stringValue", endpoint.serviceName())));
        }
        if (endpoint.ipv4() != null) {
            attributes.add(map(
                    "key", "ipv4",
                    "value", Map.of(
                            "stringValue", endpoint.ipv4())));
        }
        if (endpoint.ipv6() != null) {
            attributes.add(map(
                    "key", "ipv6",
                    "value", Map.of(
                            "stringValue", endpoint.ipv6())));
        }
        if (endpoint.port() > 0) {
            attributes.add(map(
                    "key", "port",
                    "value", Map.of(
                            "intValue", endpoint.port())));
        }
    }

    private boolean isHttpError(final Object code) {
        return (code instanceof Number n && n.intValue() > 399) ||
                (code instanceof String s && (s.startsWith("4") || s.startsWith("5")));
    }

    private Map<String, Object> toJson(final String key, final Object value) {
        if (value instanceof Boolean) {
            return map(
                    "key", key,
                    "value", Map.of("boolValue", value));
        }
        if (value instanceof Integer) {
            return map(
                    "key", key,
                    "value", Map.of("intValue", value));
        }
        if (value instanceof Number b) {
            return map(
                    "key", key,
                    "value", Map.of("doubleValue", b.doubleValue()));
        }
        if (value instanceof List<?> list) {
            return map(
                    "key", key,
                    "value", Map.of("arrayValue", list.stream()
                            .map(v -> {
                                if (v instanceof Boolean) {
                                    return Map.of("boolValue", v);
                                }
                                if (v instanceof Integer) {
                                    return Map.of("intValue", v);
                                }
                                if (v instanceof Number bd) {
                                    return Map.of("doubleValue", bd.doubleValue());
                                }
                                return Map.of("stringValue", v.toString());
                            })
                            .toList()));
        }
        return map(
                "key", key,
                "value", mi("stringValue", value.toString()));
    }

    private Map<String, Object> map(final String key1, final Object v1, final String key2, final Object v2) {
        final var out = new LinkedHashMap<String, Object>();
        out.put(key1, v1);
        out.put(key2, v2);
        return out;
    }

    private Map<String, Object> map(final String key1, final Object v1, final String key2, final Object v2, final String key3, final Object v3) {
        final var out = map(key1, v1, key2, v2);
        out.put(key3, v3);
        return out;
    }

    public static class SpansException extends RuntimeException {
        private final byte[] payload;

        public SpansException(final String message, final Throwable cause) {
            super(message, cause);
            this.payload = null;
        }

        private SpansException(final byte[] message) {
            super("");
            this.payload = message;
        }

        @Override
        public String getMessage() {
            return getMessage().isBlank() ? new String(payload, StandardCharsets.UTF_8) : getMessage();
        }
    }
}
