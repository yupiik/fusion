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
package io.yupiik.fusion.documentation;

import io.yupiik.fusion.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collector;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

/**
 * Converts a partial fusion openrpc to an openapi content which can be used with swagger ui
 * (just hacking with a request interceptor to use the server url instead of the request url since path are actually methods).
 *
 * <pre>
 *     <code>
 *  requestInterceptor: function (request) {
 *    if (request.loadSpec) {
 *      return request;
 *    }
 *    var method = request.url.substring(request.url.lastIndexOf('/') + 1);
 *    return Object.assign(request, {
 *      url: spec.servers.filter(function (server) { return request.url.indexOf(server.url) === 0; })[0].url,
 *      body: JSON.stringify({ jsonrpc: '2.0', method: method, params: JSON.parse(request.body) }, undefined, 2)
 *    });
 *  }
 *     </code>
 * </pre>
 */
public class OpenRPC2OpenAPI extends BaseOpenRPCConverter {
    public OpenRPC2OpenAPI(final Map<String, String> configuration) {
        super(configuration);
    }

    // enables to add tags for examples with some custom logic
    protected Map<String, Object> process(final Map<String, Object> out) {
        return out;
    }

    @Override
    protected String preProcessInput(final String input) {
        return input.replace("\"#/schemas/", "\"#/components/schemas/");
    }

    @Override
    public String convert(final Map<String, Object> openrpc, final JsonMapper mapper) {
        // suffix .url/.description to any prefix for servers
        final var servers = configuration.keySet().stream()
                .filter(it -> it.endsWith(".url"))
                .map(it -> {
                    final var prefix = it.substring(0, it.length() - ".url".length());
                    final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    out.put("url", configuration.get(it));
                    out.put("description", configuration.getOrDefault(prefix + ".description", prefix));
                    return out;
                })
                .sorted(comparing(m -> m.get("url").toString()))
                .toList();

        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.put("openapi", configuration.getOrDefault("openApiVersion", "3.0.3" /* swagger ui does not like 3.1.0 for ex */));
        out.put("servers", servers.isEmpty() ? List.of(Map.of("url", "http://localhost:8080/jsonrpc")) : servers);

        // extract anything starting with info.x and set it as x key
        // common/required keys are title, description and versions
        out.put("info", configuration.keySet().stream()
                .filter(it -> it.startsWith("info."))
                .collect(toMap(
                        i -> i.substring("info.".length()), configuration::get,
                        (a, b) -> a, () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))));

        out.put("paths", createPaths(openrpc));
        out.put("components", createComponents(openrpc));

        return mapper.toString(process(out));
    }

    protected Map<String, Object> createPaths(final Map<String, Object> openRpc) {
        final var methods = openRpc.get("methods");
        if (!(methods instanceof Map<?, ?> mtd)) {
            return Map.of();
        }
        final var voidSchema = Map.of("type", "object");
        return mtd.entrySet().stream()
                .collect(Collector.of(
                        () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER),
                        (a, i) -> a.put(
                                '/' + i.getKey().toString(),
                                Map.of("post", toMethod(voidSchema, asObject(i.getValue())))),
                        (a, b) -> {
                            a.putAll(b);
                            return a;
                        }));
    }

    protected Map<String, Object> toMethod(final Map<String, ?> voidSchema,
                                           final Map<String, Object> method) {
        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ofNullable(method.get("name"))
                .map(Object::toString)
                .ifPresent(value -> out.put("operationId", value));
        ofNullable(method.get("description"))
                .map(Object::toString)
                .ifPresent(value -> out.put("summary", value));
        out.put("requestBody", createRequestBody(method));
        out.put("responses", createResponses(voidSchema, method));
        return out;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> createRequestBody(final Map<String, Object> method) {
        final var params = toJsonSchema((List<Map<String, Object>>) method.get("params"));
        return Map.of("content", Map.of("application/json", Map.of("schema", wrapParams(params, method))));
    }

    protected Map<String, Object> wrapParams(final Map<String, ?> params, final Map<String, Object> method) {
        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.put("type", "object");
        out.put("required", List.of("jsonrpc", "method"));

        final var name = method.get("name");

        final var methodValue = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        methodValue.put("type", "string");
        methodValue.put("default", name == null ? "" : name);
        methodValue.put("description", "The JSON-RPC method name, should always be '" + name + "'");

        final var jsonrpc = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        jsonrpc.put("type", "string");
        jsonrpc.put("default", "2.0");
        jsonrpc.put("description", "JSON-RPC version, should always be '2.0'.");

        final var properties = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        properties.put("jsonrpc", jsonrpc);
        properties.put("method", methodValue);
        properties.put("params", params);
        out.put("properties", properties);
        return out;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> createResponses(final Map<String, ?> voidSchema,
                                                  final Map<String, Object> method) {
        final var result = method.get("result");
        final var resultSchema = result == null ?
                voidSchema :
                stripId((Map<String, Object>) ((Map<String, Object>) result).get("schema"));
        final var ok = create200Response(asObject(resultSchema));
        final var base = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        base.put("200", ok);

        final var errors = method.get("errors");
        if (!(errors instanceof Collection<?> errorList)) {
            return base;
        }

        errorList.stream()
                .map(this::asObject)
                .forEach(it -> base.put("x-jsonrpc-code=" + it.get("code"), createErrorResponse(it)));

        return base;
    }

    protected Map<String, Object> createErrorResponse(final Map<String, Object> error) {
        final var code = error.get("code");
        final var data = error.get("data");
        final var message = error.get("message");
        final var schema = data == null ? Map.of("type", "object") : stripId(asObject(data));
        final var errorCode = "Error code=" + code;

        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.put("description", ofNullable(message).map(it -> it + " (" + errorCode + ")").orElse(errorCode));
        out.put("content", Map.of("application/json",
                Map.of("schema", wrapError(((Number) code).intValue(), message == null ? "" : message.toString(), asObject(schema)))));
        return out;
    }

    protected Map<String, Object> create200Response(final Map<String, Object> resultSchema) {
        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.put("description", "OK");
        out.put("content", Map.of("application/json", Map.of("schema", wrapResult(resultSchema))));
        return out;
    }

    protected Map<String, Object> wrapError(final int code, final String message, final Map<String, Object> schema) {
        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.put("type", "object");
        out.put("required", List.of("jsonrpc", "error"));

        final var codeSchema = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        codeSchema.put("type", "integer");
        codeSchema.put("default", code);
        codeSchema.put("description", "A Number that indicates the error type that occurred. This MUST be an integer.");

        final var messageSchema = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        messageSchema.put("type", "string");
        messageSchema.put("default", message);
        messageSchema.put("description", "A String providing a short description of the error. The message SHOULD be limited to a concise single sentence.");

        final var errorProperties = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        errorProperties.put("code", codeSchema);
        errorProperties.put("message", messageSchema);
        errorProperties.put("data", schema);

        final var error = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        error.put("type", "object");
        error.put("required", List.of("code", "message"));
        error.put("properties", errorProperties);

        final var jsonrpc = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        jsonrpc.put("type", "string");
        jsonrpc.put("default", "2.0");
        jsonrpc.put("description", "JSON-RPC version, should always be '2.0'.");

        final var properties = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        properties.put("jsonrpc", jsonrpc);
        properties.put("error", error);
        out.put("properties", properties);

        return out;
    }

    protected Map<String, Object> wrapResult(final Map<String, Object> result) {
        final var jsonrpc = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        jsonrpc.put("type", "string");
        jsonrpc.put("default", "2.0");
        jsonrpc.put("description", "JSON-RPC version, should always be '2.0'.");

        final var properties = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        properties.put("jsonrpc", jsonrpc);
        properties.put("result", result);

        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.put("type", "object");
        out.put("required", List.of("jsonrpc", "result"));
        out.put("properties", properties);
        return out;
    }

    protected Map<String, Object> createComponents(final Map<String, Object> openRpc) {
        return Map.of("schemas", createSchemas(openRpc));
    }

    protected Map<String, Object> createSchemas(final Map<String, Object> openRpc) {
        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.putAll(asObject(openRpc.get("schemas")).entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> stripId(asObject(e.getValue())))));
        return out;
    }

    protected Map<String, Object> stripId(final Map<String, Object> jsonObject) {
        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.putAll(jsonObject.entrySet().stream()
                .filter(it -> !"$id".equals(it.getKey()))
                .collect(toMap(Map.Entry::getKey, entry -> {
                    if (entry.getValue() instanceof Collection<?> list) {
                        return list.stream()
                                .map(i -> i instanceof Map<?, ?> map ? stripId(asObject(map)) : i)
                                .toList();
                    } else if (entry.getValue() instanceof Map<?, ?> map) {
                        return stripId(asObject(map));
                    }
                    return entry.getValue();
                })));
        return out;
    }

    protected Map<String, Object> toJsonSchema(final Collection<Map<String, Object>> params) {
        final var required = new ArrayList<String>();
        final var base = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        base.put("type", "object");
        base.put("properties", params.stream()
                .peek(it -> {
                    if (it.get("required") instanceof Boolean r && r) {
                        required.add(it.get("name").toString());
                    }
                })
                .collect(Collector.of(
                        () -> new LinkedHashMap<String, Object>(),
                        (a, i) -> a.put(i.get("name").toString(), stripId(asObject(i.get("schema")))),
                        (m1, m2) -> {
                            m1.putAll(m2);
                            return m1;
                        })));
        if (required.isEmpty()) {
            return base;
        }
        base.put("required", required);
        return base;
    }
}
