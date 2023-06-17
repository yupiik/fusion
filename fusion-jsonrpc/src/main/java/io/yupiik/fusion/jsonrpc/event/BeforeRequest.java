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
package io.yupiik.fusion.jsonrpc.event;

import io.yupiik.fusion.http.server.api.Request;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public record BeforeRequest(
        // IN: list of JSON-RPC requests - in bulk or not cases (this is normalized)
        List<Map<String, Object>> requests,
        // IN: HTTP request
        Request request,
        // OUT: you can append to this list a promise to ensure runtime awaits for completion of your "before" task before running the method
        List<CompletionStage<?>> promises) {
}
