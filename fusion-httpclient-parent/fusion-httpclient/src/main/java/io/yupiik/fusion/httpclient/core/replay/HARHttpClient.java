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

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class HARHttpClient extends DelegatingHttpClient.Synchronous {
    private final Iterator<BaseHARDumperListener.Har.Entry> iterator;
    private final HARHelper helper = new HARHelper();

    public HARHttpClient(final Configuration configuration) {
        super(configuration.httpClient);
        iterator = configuration.har.log().entries().iterator();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
        final var next = nextEntry();
        if (next == null) {
            if (delegate == null) {
                throw new IllegalStateException("No response for " + request);
            }
            return super.sendAsync(request, handler);
        }
        try {
            return completedFuture(helper.toResponse(request, handler, next));
        } catch (final RuntimeException re) {
            final var future = new CompletableFuture<HttpResponse<T>>();
            future.completeExceptionally(re);
            return future;
        }
    }

    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
        final var next = nextEntry();
        if (next == null) {
            if (delegate == null) {
                throw new IllegalStateException("No response for " + request);
            }
            return super.send(request, responseBodyHandler);
        }
        return helper.toResponse(request, responseBodyHandler, next);
    }

    private synchronized BaseHARDumperListener.Har.Entry nextEntry() {
        return iterator.hasNext() ? iterator.next() : null;
    }

    public static class Configuration {
        private final HttpClient httpClient;
        private final BaseHARDumperListener.Har har;

        /**
         * @param httpClient the http client to use when no matching request is found.
         * @param har        the HAR to use to mock responses.
         */
        public Configuration(final HttpClient httpClient, final BaseHARDumperListener.Har har) {
            this.httpClient = httpClient;
            this.har = har;
        }

        /**
         * Usable when all responses are mocked only.
         *
         * @param har the HAR to use to mock responses.
         */
        public Configuration(final BaseHARDumperListener.Har har) {
            this(null, har);
        }
    }
}
