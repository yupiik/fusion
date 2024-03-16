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
package io.yupiik.fusion.opentelemetry.zipkin;

import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.tracing.opentelemetry.OpenTelemetryFlusher;
import io.yupiik.fusion.tracing.opentelemetry.OpenTelemetryFlusherConfiguration;
import io.yupiik.fusion.tracing.span.Span;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.time.LocalTime.MIN;
import static java.time.ZoneOffset.UTC;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenTelemetryFlusherTest {
    @Test
    void post() throws Exception {
        final var payloads = new ArrayList<String>();
        final var server = HttpServer.create(new InetSocketAddress(0), 64);
        server.createContext("/").setHandler(ex -> {
            try {
                try (final var reader = ex.getRequestBody()) {
                    payloads.add(new String(reader.readAllBytes(), StandardCharsets.UTF_8));
                }
                ex.sendResponseHeaders(200, 0);
            } finally {
                ex.close();
            }
        });

        final var endpoint = new Span.Endpoint("test", "1.2.3.4", null, 6543);
        final var span = new Span(
                "ba8c19fd8b342b534a8dc212573c4055", null, "beeca1383d3dc369", "the span", "CLIENT",
                TimeUnit.MILLISECONDS.toNanos(LocalDate.of(2024, 1, 1).atTime(MIN).atOffset(UTC).toInstant().toEpochMilli()), 100L, null, endpoint,
                Map.of("foo", "bar"), null, null, null);

        server.start();

        final var configuration = new OpenTelemetryFlusherConfiguration();
        configuration.setUrls(List.of("http://localhost:" + server.getAddress().getPort() + "/otlp"));

        try (final var jsonMapper = new JsonMapperImpl(List.of(), c -> empty())) {
            new OpenTelemetryFlusher(jsonMapper, HttpClient.newHttpClient(), configuration).accept(List.of(span));
        } finally {
            server.stop(0);
        }

        assertEquals(1, payloads.size());
        assertEquals(
                "{\"resourceSpans\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"test\"}}]},\"scopeSpans\":[{\"scope\":{\"name\":\"fusion-tracing\"},\"spans\":[{\"traceId\":\"ba8c19fd8b342b534a8dc212573c4055\",\"spanId\":\"beeca1383d3dc369\",\"name\":\"the span\",\"kind\":3,\"startTimeUnixNano\":9223372036854775807,\"endTimeUnixNano\":9223372036854775807,\"attributes\":[{\"key\":\"foo\",\"value\":{\"stringValue\":\"bar\"}},{\"key\":\"serviceName\",\"value\":{\"stringValue\":\"test\"}},{\"key\":\"ipv4\",\"value\":{\"stringValue\":\"1.2.3.4\"}},{\"key\":\"port\",\"value\":{\"intValue\":6543}}],\"status\":{\"code\":1}}]}]}]}",
                payloads.get(0));
    }
}
