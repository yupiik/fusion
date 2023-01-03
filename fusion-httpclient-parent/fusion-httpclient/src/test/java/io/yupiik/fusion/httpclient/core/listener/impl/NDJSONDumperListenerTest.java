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
package io.yupiik.fusion.httpclient.core.listener.impl;

import io.yupiik.fusion.httpclient.core.request.UnlockedHttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static java.time.Clock.fixed;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NDJSONDumperListenerTest {
    @Test
    void ndJson(@TempDir final Path temp) throws Exception {
        final var output = temp.resolve("ndjson.json");
        final var line = "{\"comment\":\"\",\"request\":{\"bodySize\":-1,\"comment\":\"\",\"headerSize\":0,\"headers\":[],\"httpVersion\":\"HTTP/1.1\",\"method\":\"GET\",\"url\":\"http://localhost:1234/test1\"},\"response\":{\"bodySize\":0,\"comment\":\"\",\"headers\":[],\"headersSize\":0,\"httpVersion\":\"HTTP/1.1\",\"status\":200,\"statusText\":\"\"},\"startedDateTime\":\"1970-01-01T00:00:00.000Z\",\"time\":0}\n";
        try (final var listener = new NDJSONDumperListener(new NDJSONDumperListener.Configuration(
                output,
                fixed(Instant.EPOCH, ZoneId.of("UTC")),
                Logger.getLogger("test")))) {
            assertEquals("", Files.readString(output));

            final var request1 = new UnlockedHttpRequest(
                    "GET", URI.create("http://localhost:1234/test1"),
                    HttpHeaders.of(Map.of(), (a, b) -> true));
            final var before1 = listener.before(1, request1);
            listener.after(before1.state(), before1.request(), null, response(200, request1));
            assertEquals(line, Files.readString(output));

            final var request2 = new UnlockedHttpRequest(
                    "GET", URI.create("http://localhost:1234/test1"),
                    HttpHeaders.of(Map.of(), (a, b) -> true));
            final var before2 = listener.before(1, request1);
            listener.after(before2.state(), before2.request(), null, response(200, request2));
            assertEquals(line + line, Files.readString(output));
        }

        assertEquals(line + line, Files.readString(output));
    }

    private HttpResponse<String> response(final int status, final HttpRequest request) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return null;
            }

            @Override
            public String body() {
                return "";
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
