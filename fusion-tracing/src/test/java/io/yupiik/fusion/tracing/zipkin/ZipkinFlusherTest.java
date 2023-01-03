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
package io.yupiik.fusion.tracing.zipkin;

import com.sun.net.httpserver.HttpServer;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.framework.JsonMapperBean;
import io.yupiik.fusion.tracing.span.Span;
import io.yupiik.fusion.tracing.span.Span$Annotation$FusionJsonCodec$FusionBean;
import io.yupiik.fusion.tracing.span.Span$Endpoint$FusionJsonCodec$FusionBean;
import io.yupiik.fusion.tracing.span.Span$FusionJsonCodec$FusionBean;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZipkinFlusherTest {
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

        final var endpoint = new Span.Endpoint(null, "1.2.3.4", null, 6543);
        final var span = new Span(1, "zios", "2", null, null, 1234L, null, null, endpoint, Map.of("foo", "bar"));

        server.start();

        final var configuration = new ZipkinFlusherConfiguration();
        configuration.setUrls(List.of("http://localhost:" + server.getAddress().getPort() + "/zipkin"));

        try (final var container = ConfiguringContainer.of()
                .disableAutoDiscovery(true)
                .register(
                        new JsonMapperBean(),
                        new Span$FusionJsonCodec$FusionBean(),
                        new Span$Annotation$FusionJsonCodec$FusionBean(),
                        new Span$Endpoint$FusionJsonCodec$FusionBean())
                .start();
             final var json = container.lookup(JsonMapper.class)) {
            new ZipkinFlusher(json.instance(), HttpClient.newHttpClient(), configuration)
                    .accept(List.of(span));
        } finally {
            server.stop(0);
        }

        assertEquals(1, payloads.size());
        assertEquals("" +
                "[{\"id\":\"2\",\"parentId\":\"zios\",\"remoteEndpoint\":{\"ipv4\":\"1.2.3.4\",\"port\":6543}," +
                "\"tags\":{\"foo\":\"bar\"},\"timestamp\":1234,\"traceId\":1}]", payloads.get(0));
    }
}
