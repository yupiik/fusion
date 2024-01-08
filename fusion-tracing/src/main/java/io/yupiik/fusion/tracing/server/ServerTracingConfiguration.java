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
package io.yupiik.fusion.tracing.server;

import java.util.Map;

public class ServerTracingConfiguration {
    private String operation = "server";
    private String serviceName = "server";
    private Map<String, Object> tags = Map.of("component", "tomcat");
    // private String parentHeader = "X-B3-ParentSpanId";
    private String spanHeader = "X-B3-SpanId";
    private String traceHeader = "X-B3-TraceId";

    public String getServiceName() {
        return serviceName;
    }

    public ServerTracingConfiguration setServiceName(final String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public String getOperation() {
        return operation;
    }

    public ServerTracingConfiguration setOperation(final String operation) {
        this.operation = operation;
        return this;
    }

    public Map<String, Object> getTags() {
        return tags;
    }

    public ServerTracingConfiguration setTags(final Map<String, Object> tags) {
        this.tags = tags;
        return this;
    }

    public String getSpanHeader() {
        return spanHeader;
    }

    public ServerTracingConfiguration setSpanHeader(final String spanHeader) {
        this.spanHeader = spanHeader;
        return this;
    }

    public String getTraceHeader() {
        return traceHeader;
    }

    public ServerTracingConfiguration setTraceHeader(final String traceHeader) {
        this.traceHeader = traceHeader;
        return this;
    }
}
