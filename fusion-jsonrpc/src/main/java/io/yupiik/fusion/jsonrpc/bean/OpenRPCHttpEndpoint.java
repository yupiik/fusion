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
package io.yupiik.fusion.jsonrpc.bean;

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.api.Response;
import io.yupiik.fusion.http.server.spi.Endpoint;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jsonrpc.JsonRpcRegistry;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Similar to {@link OpenRPCEndpoint} but registers the openrpc document directly over http without passing thru JSON-RPC methods.
 * <p>
 * IMPORTANT: current implementation requires {@link OpenRPCEndpoint} to be registered too and method be named {@code openrpc}.
 * <p>
 * It is often useful for UI which are not JSON-RPC friendly.
 */
public class OpenRPCHttpEndpoint implements Endpoint {
    private final RuntimeContainer container;

    private String binding = "/openrpc";

    private volatile CompletionStage<String> response;

    public OpenRPCHttpEndpoint(final RuntimeContainer container) {
        this.container = container;
    }

    public OpenRPCHttpEndpoint setBinding(final String binding) {
        this.binding = binding;
        return this;
    }

    @Override
    public boolean matches(final Request request) {
        return Objects.equals("GET", request.method()) && Objects.equals(binding, request.path());
    }

    @Override
    public CompletionStage<Response> handle(final Request request) {
        if (response == null) {
            synchronized (this) {
                if (response == null) {
                    try (final var reg = container.lookup(JsonRpcRegistry.class)) {
                        final var openrpc = reg.instance().methods().get("openrpc");
                        if (openrpc == null) {
                            throw new IllegalStateException("No method 'openrpc' in JsonRpcRegistry, ensure to register OpenRPCEndpoint");
                        }
                        try (final var jsonMapper = container.lookup(JsonMapper.class)) {
                            response = openrpc.invoke(null).thenApply(jsonMapper.instance()::toString);
                        }
                    }
                }
            }
        }
        return response.thenApply(body -> Response.of()
                .header("content-type", "application/json")
                .body(body)
                .build());
    }
}
