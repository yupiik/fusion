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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.util.Map.entry;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

/**
 * Converts a partial fusion openrpc to an asciidoctor content.
 */
public class OpenRPC2Adoc extends BaseOpenRPCConverter {
    public OpenRPC2Adoc(final Map<String, String> configuration) {
        super(configuration);
    }

    @Override
    public String convert(final Map<String, Object> openrpc, final JsonMapper ignored) {
        final var methods = openrpc.get("methods");
        if (!(methods instanceof Map<?, ?> mtd)) {
            return "";
        }

        final var schemas = asObject(openrpc.getOrDefault("schemas", Map.of()));
        return "== Methods\n" +
                "\n" +
                mtd.entrySet().stream()
                        .map(e -> entry(e.getKey().toString(), asObject(e.getValue())))
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> toAdoc(schemas, e.getValue()))
                        .collect(joining("\n\n", "", "\n\n")) +
                "== Schemas\n" +
                "\n" +
                schemas.entrySet().stream()
                        .map(it -> toSchemaAdoc(schemas, it.getKey(), asObject(it.getValue())))
                        .filter(Predicate.not(String::isBlank))
                        .collect(joining("\n\n", "", "\n\n"));
    }

    private String toSchemaAdoc(final Map<String, Object> schemas, final String name, final Map<String, Object> schema) {
        if (schema.getOrDefault("type", "").equals("string") && schemas.containsKey("enum")) {
            return "";
        }

        final var properties = asObject(schema.getOrDefault("properties", Map.of()));
        if (properties.isEmpty()) {
            return "";
        }

        return "=== " + schema.getOrDefault("title", name) + " (" + name + ") schema\n" +
                "\n" +
                "[cols=\"m,1a,m,3a\"]\n" +
                "|===\n" +
                "|Name|Type|Nullable|Description\n" +
                properties.entrySet().stream()
                        .map(e -> {
                            final var model = asObject(e.getValue());
                            return "\n" +
                                    "|" + e.getKey() + "\n" +
                                    "|" + type(schemas, model, new HashSet<>()) + "\n" +
                                    "|" + (model.get("nullable") instanceof Boolean b && b) + "\n" +
                                    "|" + (model.get("description") instanceof String s ? s: "-") + "\n";
                        })
                        .collect(joining()) +
                "|===";
    }

    private String toAdoc(final Map<String, Object> schemas, final Map<String, Object> method) {
        final var params = method.get("params");
        return "=== " + method.get("name") + "\n" +
                "\n" +
                "Parameter structure: " + method.getOrDefault("paramStructure", "either") + ".\n" +
                "\n" +
                method.getOrDefault("description", method.getOrDefault("summary", "")) + "\n" +
                "\n" +
                (params == null || params instanceof List<?> l && l.isEmpty() ?
                        "This method does not have any parameter.\n" :
                        "Parameters:\n" +
                                ((List<?>) params).stream()
                                        .map(this::asObject)
                                        .map(p -> "* `" + p.getOrDefault("name", "?") + "`" +
                                                ofNullable(type(schemas, asObject(p.get("schema")), new HashSet<>())).map(it -> " (" + it + ')').orElse("") +
                                                ofNullable(p.get("description")).map(i -> ": " + i).orElse(""))
                                        .collect(joining("\n", "", "\n\n")));
    }

    private String type(final Map<String, Object> schemas, final Map<String, Object> schema, final Collection<String> visited) {
        final var ref = schema.get("$ref");
        if (ref != null && visited.add(ref.toString())) {
            final var referencedSchema = schemas.get(ref.toString().substring("#/schemas/".length()));
            if (referencedSchema != null) {
                return type(schemas, asObject(referencedSchema), visited);
            }
        }

        final var type = schema.getOrDefault("type", "unknown").toString();
        return switch (type) {
            case "string" -> {
                if (schema.get("enum") instanceof List<?> l) {
                    yield "enum with potential values " + l.stream().map(Object::toString).map(it -> '`' + it + '`').collect(joining(", "));
                }
                yield "`string`";
            }
            case "boolean" -> "`boolean`";
            case "int" -> "`integer`";
            case "long" -> "`long`";
            case "date-time" -> "`date-time`";
            case "date" -> "`date`";
            case "array" ->
                    "array with items of type " + ofNullable(type(schemas, asObject(schema.getOrDefault("items", Map.of())), visited)).orElse("unknown");
            case "object" -> "object of type ``";
            default -> type;
        };
    }
}
