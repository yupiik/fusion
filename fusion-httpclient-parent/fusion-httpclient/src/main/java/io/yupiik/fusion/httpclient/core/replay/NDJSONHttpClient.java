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
package io.yupiik.fusion.httpclient.core.replay;

import io.yupiik.fusion.httpclient.core.DelegatingHttpClient;
import io.yupiik.fusion.httpclient.core.listener.impl.BaseHARDumperListener;
import io.yupiik.fusion.httpclient.core.listener.impl.har.HarJsonMapper;
import io.yupiik.fusion.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class NDJSONHttpClient extends DelegatingHttpClient.Synchronous implements AutoCloseable {
    private final JsonMapper mapper = new HarJsonMapper();
    private final BufferedReader stream;
    private final HARHelper helper = new HARHelper();

    public NDJSONHttpClient(final Configuration configuration) {
        super(configuration.httpClient);
        try {
            this.stream = Files.newBufferedReader(configuration.input);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        final var line = nextLine();
        if (line == null) {
            if (delegate == null) {
                throw new IllegalStateException("No response for " + request);
            }
            return super.send(request, responseBodyHandler);
        }
        return helper.toResponse(request, responseBodyHandler, readLine(line));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
        final String line;
        try {
            line = nextLine();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        if (line == null) {
            if (delegate == null) {
                throw new IllegalStateException("No response for " + request);
            }
            return super.sendAsync(request, handler);
        }
        try {
            return completedFuture(helper.toResponse(request, handler, readLine(line)));
        } catch (final RuntimeException re) {
            final var future = new CompletableFuture<HttpResponse<T>>();
            future.completeExceptionally(re);
            return future;
        }
    }

    private BaseHARDumperListener.Har.Entry readLine(final String line) {
        return mapper.fromString(BaseHARDumperListener.Har.Entry.class, line.strip());
    }

    private synchronized String nextLine() throws IOException {
        while (true) {
            final var line = stream.readLine();
            if (line == null || !line.isBlank()) {
                return line;
            }
        }
    }

    @Override
    public void close() throws Exception {
        mapper.close();
        stream.close();
    }

    public static class Configuration {
        private final HttpClient httpClient;
        private final Path input;

        /**
         * @param httpClient the http client to use when no matching request is found.
         * @param input      the ND-JSON input.
         */
        public Configuration(final HttpClient httpClient, final Path input) {
            this.httpClient = httpClient;
            this.input = input;
        }

        /**
         * Usable when all responses are mocked only.
         *
         * @param input the ND-JSON input.
         */
        public Configuration(final Path input) {
            this(null, input);
        }
    }
}
