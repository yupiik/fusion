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
package io.yupiik.fusion.jsonrpc;

import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;

import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class JsonRpcRegistry {
    private final Map<String, JsonRpcMethod> methods;

    public JsonRpcRegistry(final List<JsonRpcMethod> methods) {
        this.methods = methods.stream().collect(toMap(JsonRpcMethod::name, identity(), (a, b) -> {
            if (a.priority() - b.priority() >= 0) {
                return a;
            }
            return b;
        }));
    }

    public Map<String, JsonRpcMethod> methods() {
        return methods;
    }
}
