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
package io.yupiik.fusion.httpclient.core.response;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

public class StaticHttpResponse<T> implements HttpResponse<T> {
    private final HttpRequest request;
    private final int statusCode;
    private final HttpHeaders headers;
    private final T body;
    private final URI uri;
    private final HttpClient.Version version;

    public StaticHttpResponse(final HttpRequest request,
                              final URI uri, final HttpClient.Version version,
                              final int statusCode, final HttpHeaders headers, final T body) {
        this.request = request;
        this.uri = uri;
        this.version = version;
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
    }

    public StaticHttpResponse(final HttpRequest request,
                              final HttpClient.Version version,
                              final int statusCode, final HttpHeaders headers, final T body) {
        this(request, request.uri(), version, statusCode, headers, body);
    }

    public StaticHttpResponse(final HttpRequest request,
                              final int statusCode, final HttpHeaders headers, final T body) {
        this(request, request.uri(), HttpClient.Version.HTTP_1_1, statusCode, headers, body);
    }

    public StaticHttpResponse(final HttpRequest request, final int statusCode, final T body) {
        this(request, request.uri(), HttpClient.Version.HTTP_1_1, statusCode, Constants.NO_HEADER, body);
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public Optional<HttpResponse<T>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public T body() {
        return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public HttpClient.Version version() {
        return version;
    }

    private static class Constants {
        private static final HttpHeaders NO_HEADER = HttpHeaders.of(Map.of(), (a, b) -> true);

        private Constants() {
            // no-op
        }
    }
}
