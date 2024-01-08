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
package io.yupiik.fusion.tracing.span;

import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.List;
import java.util.Map;

@JsonModel
public record Span(Object traceId, Object parentId, Object id, String name,
                   String kind, Long timestamp, Long duration,
                   Endpoint localEndpoint, Endpoint remoteEndpoint,
                   Map<String, Object> tags, List<Annotation> annotations,
                   Boolean shared, Boolean debug) {
    @JsonModel
    public record Endpoint(String serviceName, String ipv4, String ipv6, int port) {
    }

    @JsonModel
    public record Annotation(String key, int type, Object value) {
    }
}
