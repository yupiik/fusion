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
package io.yupiik.fusion.httpclient.core.listener.impl;

import io.yupiik.fusion.httpclient.core.listener.RequestListener;
import io.yupiik.fusion.httpclient.core.request.UnlockedHttpRequest;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Usage:
 *
 * {@code new DefaultTimeout(Duration.ofSeconds(clientConfiguration.getTimeout()))}
 */
public class DefaultTimeout implements RequestListener<Void> {
    private final Optional<Duration> timeout;

    public DefaultTimeout(final Duration timeout) {
        this.timeout = ofNullable(timeout);
    }

    @Override
    public State<Void> before(final long count, final HttpRequest request) {
        if (request.timeout().isEmpty()) {
            return new State<>(
                    new UnlockedHttpRequest(
                            request.bodyPublisher(),
                            request.method(),
                            timeout,
                            request.expectContinue(),
                            request.uri(),
                            request.version(),
                            request.headers()),
                    null);
        }
        return new State<>(request, null);
    }
}
