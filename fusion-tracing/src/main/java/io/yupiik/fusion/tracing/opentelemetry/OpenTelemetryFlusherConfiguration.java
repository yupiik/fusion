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
package io.yupiik.fusion.tracing.opentelemetry;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

public class OpenTelemetryFlusherConfiguration {
    /**
     * Should the spans be gzipped.
     */
    private boolean gzip;

    /**
     * URLs to tests of remote HTTP collector.
     * Default path is generally something like {@code /v1/traces}.
     * For example for {@code docker run -p 4318:4318 -p 55679:55679 otel/opentelemetry-collector-contrib:0.96.0}
     * it is {@code http://localhost:4318/v1/traces}.
     */
    private List<String> urls = List.of();

    /**
     * Optional headers to set when posting the spans.
     */
    private Map<String, String> headers = Map.of();

    /**
     * In real life we tend to recommend something around 1-2 seconds, default also supports dev/demo envs but is too high.
     */
    private Duration timeout = Duration.of(30, ChronoUnit.SECONDS);

    public boolean isGzip() {
        return gzip;
    }

    public OpenTelemetryFlusherConfiguration setGzip(final boolean gzip) {
        this.gzip = gzip;
        return this;
    }

    public List<String> getUrls() {
        return urls;
    }

    public OpenTelemetryFlusherConfiguration setUrls(final List<String> urls) {
        this.urls = urls;
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public OpenTelemetryFlusherConfiguration setHeaders(final Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public OpenTelemetryFlusherConfiguration setTimeout(final Duration timeout) {
        this.timeout = timeout;
        return this;
    }
}
