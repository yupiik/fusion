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
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.framework.JsonMapperBean;
import io.yupiik.fusion.jsonrpc.impl.bean.ResponseJsonCodecBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseTest {
    @Test
    void ensureIdCanBeStringOrNumber() {
        try (final var container = ConfiguringContainer.of()
                .disableAutoDiscovery(true)
                .register(new ResponseJsonCodecBean(), new JsonMapperBean())
                .start();
             final var json = container.lookup(JsonMapper.class)) {
            assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1}", json.instance().toString(new Response("2.0", 1, null, null)));
            assertEquals("{\"jsonrpc\":\"2.0\",\"id\":\"1\"}", json.instance().toString(new Response("2.0", "1", null, null)));
        }
    }
}
