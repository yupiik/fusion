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
package io.yupiik.fusion.kubernetes.client;

import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class WebSocketBuilderDelegate implements WebSocket.Builder {
    private final WebSocket.Builder delegate;

    public WebSocketBuilderDelegate(final WebSocket.Builder builder) {
        this.delegate = builder;
    }

    @Override
    public WebSocket.Builder header(final String name, final String value) {
        delegate.header(name, value);
        return this;
    }

    @Override
    public WebSocket.Builder connectTimeout(final Duration timeout) {
        delegate.connectTimeout(timeout);
        return this;
    }

    @Override
    public WebSocket.Builder subprotocols(final String mostPreferred, final String... lesserPreferred) {
        delegate.subprotocols(mostPreferred, lesserPreferred);
        return this;
    }

    @Override
    public CompletableFuture<WebSocket> buildAsync(final URI uri, final WebSocket.Listener listener) {
        return delegate.buildAsync(uri, listener);
    }
}
