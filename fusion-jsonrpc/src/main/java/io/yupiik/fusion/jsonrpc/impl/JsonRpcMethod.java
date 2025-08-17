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
package io.yupiik.fusion.jsonrpc.impl;

import io.yupiik.fusion.http.server.api.Request;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface JsonRpcMethod {
    default int priority() {
        return 1000;
    }

    String name();

    default boolean isNotification() {
        return false;
    }

    CompletionStage<?> invoke(Context context);

    default Map<String, String> metadata() {
        return Map.of();
    }

    record Context(Request request, Object params) {
    }
}
