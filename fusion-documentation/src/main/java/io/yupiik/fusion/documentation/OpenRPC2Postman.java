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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

public class OpenRPC2Postman extends BaseOpenRPCConverter {
    public OpenRPC2Postman(final Map<String, String> configuration) {
        super(configuration);
    }

    @Override
    public String convert(final Map<String, Object> openrpc, final JsonMapper mapper) {
        final var info = sortedMap(Map.of(
                "title", requireNonNull(configuration.get("info.title"), "No info.title set"),
                "version", requireNonNull(configuration.get("info.version"), "No info.version set"),
                "description", configuration.getOrDefault("info.description", ""),
                "schema", configuration.getOrDefault("info.schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json")));

        final var endpointVariable = sortedMap(Map.of(
                "key", "JSON_RPC_ENDPOINT",
                "value", configuration.getOrDefault("server.url", "http://localhost:8080/jsonrpc"),
                "type", "text",
                "description", "JSON-RPC endpoint."));

        final var schemas = ofNullable(openrpc.get("schemas"))
                .map(this::asObject)
                .orElse(Map.of());
        final var items = openrpc.get("methods") instanceof Map<?, ?> methods ?
                asObject(methods).values().stream()
                        .map(this::asObject)
                        .map(it -> toPostman(it, mapper, schemas))
                        .toList()
                : List.of();

        return mapper.toString(sortedMap(Map.of(
                "schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
                "info", info,
                "items", items,
                "variables", List.of(endpointVariable))));
    }

    protected Object toPostman(final Map<String, Object> method, final JsonMapper mapper, final Map<String, Object> schemas) {
        final var name = String.valueOf(method.get("name"));
        return sortedMap(Map.of(
                "name", name,
                "description", String.valueOf(method.getOrDefault("description", "Method '" + name + "'")),
                "variables", List.of(),
                "request", sortedMap(Map.of(
                        "url", "{{JSON_RPC_ENDPOINT}}",
                        "method", "POST",
                        "description", "JSON-RPC endpoint.",
                        "body", sortedMap(Map.of("mode", "raw", "raw", toSampleRequest(mapper, name, method.get("params"), schemas), "options", Map.of())),
                        "header", Stream.of("Accept", "Content-Type")
                                .map(it -> sortedMap(Map.of("type", "text", "key", it, "value", "application/json", "description", it + " header must be application/json.")))
                                .toList())),
                "responses", List.of()));
    }

    protected String toSampleRequest(final JsonMapper mapper, final String name, final Object paramsSpec, final Map<String, Object> schemas) {
        final var out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        out.put("jsonrpc", "2.0");
        out.put("method", name);
        if (paramsSpec instanceof List<?> l && !l.isEmpty()) {
            out.put("params", l.stream()
                    .map(this::asObject)
                    .collect(toMap(
                            p -> String.valueOf(p.get("name")),
                            p -> toSampleParam(asObject(p.get("schema")), schemas),
                            (m1, m2) -> m1,
                            () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))));
        }
        return mapper.toString(out);
    }

    protected Object toSampleParam(final Map<String, Object> schema, final Map<String, Object> schemas) {
        if (schema == null || schema.isEmpty()) {
            return Map.of();
        }

        final var ref = schema.get("$ref");
        if (ref != null && ref.toString().startsWith("#/schemas/")) {
            return toSampleParam(asObject(schemas.get(ref.toString().substring("#/schemas/".length()))), schemas);
        }

        return switch (schema.getOrDefault("type", "object").toString()) {
            case "date" -> "2024-01-29";
            case "date-time" -> "2024-01-29T18:20:39+00:00";
            case "string" -> "<string>";
            case "number", "integer" -> 1234;
            case "bool", "boolean" -> true;
            case "array" -> {
                final var items = schema.getOrDefault("items", Map.of());
                if (items != null) {
                    yield List.of(toSampleParam(asObject(items), schemas));
                }
                yield List.of();
            }
            case "object" -> {
                final var props = schema.getOrDefault("properties", Map.of());
                if (props instanceof Map<?, ?> properties) {
                    yield sortedMap(properties.entrySet().stream()
                            .map(e -> entry(e.getKey().toString(), asObject(e.getValue())))
                            .collect(toMap(Map.Entry::getKey, e -> toSampleParam(e.getValue(), schemas))));
                }
                yield Map.of();
            }
            default -> Map.of();
        };
    }
}
