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
package io.yupiik.fusion.jsonrpc.impl.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jsonrpc.JsonRpcEndpoint;
import io.yupiik.fusion.jsonrpc.JsonRpcHandler;

import java.util.List;
import java.util.Map;

public class JsonRpcEndpointBean extends BaseBean<JsonRpcEndpoint> {
    public JsonRpcEndpointBean() {
        super(JsonRpcEndpoint.class, ApplicationScoped.class, 1000, Map.of());
    }

    @Override
    public JsonRpcEndpoint create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        try (final var config = container.lookup(Configuration.class)) {
            return new JsonRpcEndpoint(
                    lookup(container, JsonRpcHandler.class, dependents),
                    lookup(container, JsonMapper.class, dependents),
                    config.instance().get("fusion.jsonrpc.binding").orElse("/jsonrpc"),
                    config.instance().get("fusion.jsonrpc.forceInputStreamUsage").map(Boolean::parseBoolean).orElse(false),
                    config.instance().get("fusion.jsonrpc.voidStatus").map(Integer::parseInt).orElse(202),
                    config.instance().get("fusion.jsonrpc.responseContentType").orElse("application/json;charset=utf-8"));
        }
    }
}
