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

import io.yupiik.fusion.httpclient.core.listener.RequestListener;
import io.yupiik.fusion.httpclient.core.request.UnlockedHttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class FilteringListenerTest {
    private final UnlockedHttpRequest getRequest = new UnlockedHttpRequest(
            "GET", URI.create("http://localhost:1234/test1"),
            HttpHeaders.of(Map.of(), (a, b) -> true));
    private final UnlockedHttpRequest postRequest = new UnlockedHttpRequest(
            "POST", URI.create("http://localhost:1234/test2?a=b"),
            HttpRequest.BodyPublishers.ofString("{}"),
            HttpHeaders.of(Map.of(), (a, b) -> true));

    @Test
    void ignoreExceptions() {
        final var collector = new Collector();
        final var filter = new FilteringListener<>(new FilteringListener.Configuration<>(collector));
        assertTrue(collector.collected.isEmpty());

        {   // passthrough
            final var before = filter.before(1, getRequest);
            final var ok = response(200, getRequest);
            filter.after(before.state(), getRequest, null, ok);
            assertEquals(1, collector.collected.size());
            assertEquals(ok, collector.collected.get(getRequest));
        }
        collector.collected.clear();
        {   // filter
            final var before = filter.before(1, getRequest);
            filter.after(before.state(), getRequest, new IllegalStateException(), null);
            assertEquals(1, collector.collected.size());
            assertNull(collector.collected.get(getRequest));
        }
    }

    @Test
    void ignoreStatus() {
        final var collector = new Collector();
        final var filter = new FilteringListener<>(new FilteringListener.Configuration<>(collector)
                .setMinimumStatus(200)
                .setMaximumStatus(499));
        assertTrue(collector.collected.isEmpty());

        {   // passthrough
            final var before = filter.before(1, getRequest);
            final var ok = response(200, getRequest);
            filter.after(before.state(), getRequest, null, ok);
            assertEquals(1, collector.collected.size());
            assertEquals(ok, collector.collected.get(getRequest));
        }
        collector.collected.clear();
        {   // filter (min)
            final var before = filter.before(1, getRequest);
            final var ok = response(199, getRequest);
            filter.after(before.state(), getRequest, null, ok);
            assertEquals(1, collector.collected.size());
            assertNull(collector.collected.get(getRequest));
        }
        collector.collected.clear();
        {   // filter (max)
            final var before = filter.before(1, getRequest);
            final var ok = response(500, getRequest);
            filter.after(before.state(), getRequest, null, ok);
            assertEquals(1, collector.collected.size());
            assertNull(collector.collected.get(getRequest));
        }
    }

    @Test
    void ignoreMethod() {
        final var collector = new Collector();
        final var filter = new FilteringListener<>(new FilteringListener.Configuration<>(collector)
                .setIgnoredMethods(List.of("GET")));
        assertTrue(collector.collected.isEmpty());

        {   // passthrough
            final var before = filter.before(1, postRequest);
            final var ok = response(200, postRequest);
            filter.after(before.state(), postRequest, null, ok);
            assertEquals(1, collector.collected.size());
            assertEquals(ok, collector.collected.get(postRequest));
        }
        collector.collected.clear();
        {   // filter
            final var before = filter.before(1, getRequest);
            final var ok = response(200, getRequest);
            filter.after(before.state(), getRequest, null, ok);
            assertTrue(collector.collected.isEmpty());
        }
    }

    @Test
    void ignorePath() {
        final var collector = new Collector();
        final var filter = new FilteringListener<>(new FilteringListener.Configuration<>(collector)
                .setIgnoredPaths(List.of("/test1")));
        assertTrue(collector.collected.isEmpty());

        {   // passthrough
            final var before = filter.before(1, postRequest);
            final var ok = response(200, postRequest);
            filter.after(before.state(), postRequest, null, ok);
            assertEquals(1, collector.collected.size());
            assertEquals(ok, collector.collected.get(postRequest));
        }
        collector.collected.clear();
        {   // filter
            final var before = filter.before(1, getRequest);
            final var ok = response(200, getRequest);
            filter.after(before.state(), getRequest, null, ok);
            assertTrue(collector.collected.isEmpty());
        }
    }

    @Test
    void ignorePathRegex() {
        final var collector = new Collector();
        final var filter = new FilteringListener<>(new FilteringListener.Configuration<>(collector)
                .setIgnoredPaths(List.of("regex:/tes.1")));
        assertTrue(collector.collected.isEmpty());

        {   // passthrough
            final var before = filter.before(1, postRequest);
            final var ok = response(200, postRequest);
            filter.after(before.state(), postRequest, null, ok);
            assertEquals(1, collector.collected.size());
            assertEquals(ok, collector.collected.get(postRequest));
        }
        collector.collected.clear();
        {   // filter
            final var before = filter.before(1, getRequest);
            final var ok = response(200, getRequest);
            filter.after(before.state(), getRequest, null, ok);
            assertTrue(collector.collected.isEmpty());
        }
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

    private static class Collector implements RequestListener<Void> {
        private final Map<HttpRequest, HttpResponse<?>> collected = new HashMap<>();

        @Override
        public State<Void> before(final long count, final HttpRequest request) {
            collected.put(request, null);
            return new State<>(request, null);
        }

        @Override
        public void after(final Void state, final HttpRequest request, final Throwable error, final HttpResponse<?> response) {
            assertTrue(collected.containsKey(request), "before should have been called before after");
            collected.put(request, response);
        }
    }
}
