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
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.json.internal.framework.JsonModule;
import io.yupiik.fusion.jsonrpc.event.BeforeRequest;
import io.yupiik.fusion.jsonrpc.impl.DefaultJsonRpcMethod;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;
import io.yupiik.fusion.jsonrpc.impl.bean.JsonRpcModule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRpcHandlerTest {
    @Test
    void beforeEvent() throws ExecutionException, InterruptedException {
        final var events = new ArrayList<BeforeRequest>();
        try (final var container = ConfiguringContainer.of()
                .disableAutoDiscovery(true)
                .register(new JsonModule(), new JsonRpcModule())
                .register(new BaseBean<JsonRpcMethod>(JsonRpcMethod.class, DefaultScoped.class, 0, Map.of()) {
                    @Override
                    public JsonRpcMethod create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return new DefaultJsonRpcMethod(0, "beforeEvent", ctx -> completedFuture(true));
                    }
                })
                .register(new FusionListener<BeforeRequest>() {
                    @Override
                    public Type eventType() {
                        return BeforeRequest.class;
                    }

                    @Override
                    public void onEvent(final RuntimeContainer container, final BeforeRequest event) {
                        events.add(event);
                    }
                })
                .start();
             final var handler = container.lookup(JsonRpcHandler.class)) {
            assertTrue(events.isEmpty(), events::toString);

            final var request = Map.of("jsonrpc", "2.0", "method", "beforeEvent");
            assertTrue((Boolean) ((Response) handler.instance()
                    .execute(request, null)
                    .toCompletableFuture()
                    .get())
                    .result());
            assertEquals(1, events.size());
            assertEquals(List.of(request), events.get(0).requests());
        }
    }
}
