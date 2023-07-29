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
package io.yupiik.fusion.jsonrpc.api;

import java.util.Map;

/**
 * Enables to force the response to have some headers (like cache ones).
 * This is only for successful responses as of today (not exceptions).
 */
public class PartialResponse {
    private final Object jsonRpcResult;
    private Map<String, String> httpResponseHeaders = Map.of();

    public PartialResponse(final Object jsonRpcResult) {
        this.jsonRpcResult = jsonRpcResult;
    }

    public PartialResponse setHttpResponseHeaders(final Map<String, String> httpResponseHeaders) {
        this.httpResponseHeaders = httpResponseHeaders;
        return this;
    }

    public Object getJsonRpcResult() {
        return jsonRpcResult;
    }

    public Map<String, String> getHttpResponseHeaders() {
        return httpResponseHeaders;
    }
}
