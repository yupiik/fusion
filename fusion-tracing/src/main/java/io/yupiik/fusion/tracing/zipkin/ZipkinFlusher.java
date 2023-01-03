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

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.tracing.span.Span;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.Consumer;

import static java.net.http.HttpResponse.BodyHandlers.ofString;

public class ZipkinFlusher implements Consumer<Collection<Span>> {
    private final JsonMapper mapper;
    private final HttpClient client;
    private final ZipkinFlusherConfiguration configuration;

    public ZipkinFlusher(final JsonMapper mapper,
                         final HttpClient client, // potentially an enriched one
                         final ZipkinFlusherConfiguration configuration) {
        this.mapper = mapper;
        this.client = client;
        this.configuration = configuration;
    }

    @Override
    public void accept(final Collection<Span> spans) {
        if (spans.isEmpty()) {
            return;
        }
        final var payload = mapper.toBytes(spans);
        final var error = new IllegalStateException("Can't send spans to zipkin");
        for (final var url : configuration.getUrls()) {
            final var requestBuilder = HttpRequest.newBuilder()
                    .timeout(configuration.getTimeout())
                    .uri(URI.create(url))
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
            configuration.getHeaders().forEach(requestBuilder::header);
            try {
                final var response = client.send(requestBuilder.build(), ofString());
                if (response.statusCode() >= 200 && response.statusCode() <= 300) {
                    return;
                }
            } catch (final IOException | RuntimeException e) {
                error.addSuppressed(e);
            } catch (final InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        // ensure it is "logged" somewhere if correctly wrapped or can be caught easily at least
        error.addSuppressed(new JsonSpans(new String(payload, StandardCharsets.UTF_8)));
        throw error;
    }

    public static class JsonSpans extends RuntimeException {
        private JsonSpans(final String message) {
            super(message);
        }
    }
}
