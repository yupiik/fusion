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
package io.yupiik.fusion.tracing.zipkin;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class ZipkinFlusherConfiguration {
    private List<String> urls = List.of();
    private Map<String, String> headers = Map.of();
    private Duration timeout = Duration.of(30, ChronoUnit.SECONDS);

    public List<String> getUrls() {
        return urls;
    }

    public ZipkinFlusherConfiguration setUrls(final List<String> urls) {
        this.urls = urls;
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public ZipkinFlusherConfiguration setHeaders(final Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public ZipkinFlusherConfiguration setTimeout(final Duration timeout) {
        this.timeout = timeout;
        return this;
    }
}
