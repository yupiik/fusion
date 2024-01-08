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

import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SetUserAgent implements RequestListener<Void> {
    private final List<String> value;

    public SetUserAgent() {
        this("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.101 Safari/537.36");
    }

    public SetUserAgent(final String value) {
        this(List.of(value));
    }

    public SetUserAgent(final List<String> value) {
        this.value = value;
    }

    @Override
    public State<Void> before(final long count, final HttpRequest request) {
        return new State<>(
                new UnlockedHttpRequest(
                        request.bodyPublisher(),
                        request.method(),
                        request.timeout(),
                        request.expectContinue(),
                        request.uri(),
                        request.version(),
                        HttpHeaders.of(addAgent(request.headers().map()), (k, v) -> true)),
                null);
    }

    private Map<String, List<String>> addAgent(final Map<String, List<String>> map) {
        final var agent = map.get("user-agent");
        if (agent == null || (!agent.isEmpty() && agent.stream().anyMatch(it -> it.contains("java")))) {
            final var newMap = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            newMap.putAll(map);
            newMap.remove("User-Agent");
            newMap.put("User-Agent", value);
            return newMap;
        }
        return map;
    }
}
