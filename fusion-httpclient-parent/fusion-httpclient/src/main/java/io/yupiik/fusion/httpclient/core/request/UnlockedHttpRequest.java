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
package io.yupiik.fusion.httpclient.core.request;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;

// enables to force some normally forbidden headers by default JRE HttpRequest builder
public class UnlockedHttpRequest extends HttpRequest {
    private final Optional<BodyPublisher> bodyPublisher;
    private final String method;
    private final Optional<Duration> timeout;
    private final boolean expectContinue;
    private final URI uri;
    private final Optional<HttpClient.Version> version;
    private final HttpHeaders headers;

    public UnlockedHttpRequest(final HttpRequest from) {
        this(from.bodyPublisher(), from.method(), from.timeout(), from.expectContinue(), from.uri(), from.version(), from.headers());
    }

    public UnlockedHttpRequest(final String method, final URI uri, final HttpHeaders headers) {
        this(empty(), method, empty(), false, uri, empty(), headers);
    }

    public UnlockedHttpRequest(final String method, final URI uri, final BodyPublisher bodyPublisher,
                               final HttpHeaders headers) {
        this(of(bodyPublisher), method, empty(), false, uri, empty(), headers);
    }

    public UnlockedHttpRequest(final Optional<BodyPublisher> bodyPublisher, final String method, final Optional<Duration> timeout,
                               final boolean expectContinue, final URI uri, final Optional<HttpClient.Version> version,
                               final HttpHeaders headers) {
        this.bodyPublisher = bodyPublisher;
        this.method = method;
        this.timeout = timeout;
        this.expectContinue = expectContinue;
        this.uri = uri;
        this.version = version;
        this.headers = headers;
    }

    @Override
    public Optional<BodyPublisher> bodyPublisher() {
        return bodyPublisher;
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public Optional<Duration> timeout() {
        return timeout;
    }

    @Override
    public boolean expectContinue() {
        return expectContinue;
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public Optional<HttpClient.Version> version() {
        return version;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }
}
