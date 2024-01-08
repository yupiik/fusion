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
package io.yupiik.fusion.httpclient.core;

import io.yupiik.fusion.httpclient.core.listener.RequestListener;

import java.net.http.HttpClient;
import java.util.List;

public class ExtendedHttpClientConfiguration {
    private HttpClient delegate;
    private List<RequestListener<?>> requestListeners = List.of();

    public ExtendedHttpClientConfiguration setDelegate(final HttpClient delegate) {
        this.delegate = delegate;
        return this;
    }

    public ExtendedHttpClientConfiguration setRequestListeners(final List<RequestListener<?>> requestListeners) {
        this.requestListeners = requestListeners;
        return this;
    }

    public HttpClient getDelegate() {
        return delegate;
    }

    public List<RequestListener<?>> getRequestListeners() {
        return requestListeners;
    }
}
