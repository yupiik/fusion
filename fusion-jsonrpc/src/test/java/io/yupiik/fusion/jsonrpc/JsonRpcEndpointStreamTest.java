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
package io.yupiik.fusion.jsonrpc;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import io.yupiik.fusion.json.internal.framework.JsonModule;
import io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;
import io.yupiik.fusion.jsonrpc.impl.bean.JsonRpcModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// end to end test of the byte stream request/response paths (InputStream unwrap + bytesBody)
class JsonRpcEndpointStreamTest {
    @Test
    void nonAsciiRoundTripOverHttp() throws IOException, InterruptedException {
        try (final var container = ConfiguringContainer.of()
                .disableAutoDiscovery(true)
                .register(new JsonModule(), new JsonRpcModule())
                .register(new BaseBean<JsonRpcMethod>(JsonRpcMethod.class, DefaultScoped.class, 0, Map.of()) {
                    @Override
                    public JsonRpcMethod create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return new DefaultJsonRpcMethod(0, "echo", ctx -> completedFuture(ctx.params()));
                    }
                })
                .start();
             final var endpoint = container.lookup(JsonRpcEndpoint.class)) {
            final var configuration = WebServer.Configuration.of().port(0);
            configuration.unwrap(TomcatWebServerConfiguration.class).setEndpoints(List.of(endpoint.instance()));
            try (final var server = WebServer.of(configuration)) {
                final var value = "e\u00e9\u00e8 \u4f60\u597d \ud83d\ude00";
                final var request = "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"echo\",\"params\":{\"v\":\"" + value + "\"}}";
                final var response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder()
                                .POST(HttpRequest.BodyPublishers.ofString(request, StandardCharsets.UTF_8))
                                .uri(URI.create("http://" + configuration.host() + ":" + configuration.port() + "/jsonrpc"))
                                .header("content-type", "application/json;charset=utf-8")
                                .build(),
                        ofString(StandardCharsets.UTF_8));
                assertEquals(200, response.statusCode(), response::body);
                assertTrue(response.body().contains("\"v\":\"" + value + "\""), response::body);
            }
        }
    }
}
