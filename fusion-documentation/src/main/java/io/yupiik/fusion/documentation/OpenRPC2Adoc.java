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
    private final String tableAttributes;

    public OpenRPC2Adoc(final Map<String, String> configuration) {
        super(configuration);
        this.tableAttributes = configuration.getOrDefault("tableAttributes", "");
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

        return "[[" + anchorOf(name) + "]]\n" +
                "=== " + schema.getOrDefault("title", name) + " (" + name + ") schema\n" +
                "\n" +
                "[cols=\"m,1a,m,3a\",opts=header" + (tableAttributes.isEmpty() ? "" : (',' + tableAttributes)) + "]\n" +
                "|===\n" +
                "|Name |Type |Nullable |Description\n" +
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
        final var result = method.get("result");
        final var paramsAdoc = params == null || params instanceof List<?> l && l.isEmpty() ?
                "This method does not have any parameter." :
                "Parameters:\n" +
                        ((List<?>) params).stream()
                                .map(this::asObject)
                                .map(p -> "* `" + p.getOrDefault("name", "?") + "`" +
                                        ofNullable(schemaLink(schemas, asObject(p.get("schema")))).map(it -> " (" + it + ')').orElse("") +
                                        ofNullable(p.get("description")).map(i -> ": " + i).orElse(""))
                                .collect(joining("\n"));
        final var resultLine = result == null ? "" : resultAdoc(schemas, asObject(result));
        return "=== " + method.get("name") + "\n" +
                "\n" +
                "Parameter structure: " + method.getOrDefault("paramStructure", "either") + ".\n" +
                "\n" +
                method.getOrDefault("description", method.getOrDefault("summary", "")) + "\n" +
                "\n" +
                paramsAdoc +
                (resultLine.isEmpty() ? "\n" : "\n\n" + resultLine + "\n");
    }

    private String resultAdoc(final Map<String, Object> schemas, final Map<String, Object> result) {
        final var schema = asObject(result.get("schema"));
        final String rendered;
        if (isOnPage(schemas, schema)) {
            rendered = schemaLink(schemas, schema);
        } else {
            rendered = type(schemas, schema, new HashSet<>());
        }
        return rendered == null || rendered.isEmpty() ? "" : "Result: " + rendered;
    }

    /**
     * Renders a schema (either a {@code $ref} to an on-page schema or an inline type) as an AsciiDoc link or
     * plain type. Returns {@code null} when nothing meaningful can be rendered.
     */
    private String schemaLink(final Map<String, Object> schemas, final Map<String, Object> schema) {
        final var ref = schema.get("$ref");
        if (ref != null) {
            final var key = ref.toString().substring("#/schemas/".length());
            if (schemas.containsKey(key) && isOnPage(schemas, key)) {
                final var onPage = asObject(schemas.get(key));
                final var title = onPage.getOrDefault("title", key).toString();
                return "xref:" + anchorOf(key) + "[`" + title + "`]";
            }
        }
        final var t = type(schemas, schema, new HashSet<>());
        return t == null || t.isEmpty() || "unknown".equals(t) ? null : t;
    }

    private boolean isOnPage(final Map<String, Object> schemas, final Map<String, Object> schema) {
        final var ref = schema.get("$ref");
        if (ref == null) {
            return false;
        }
        final var key = ref.toString().substring("#/schemas/".length());
        return isOnPage(schemas, key);
    }

    private boolean isOnPage(final Map<String, Object> schemas, final String key) {
        final var schema = schemas.get(key);
        if (schema == null) {
            return false;
        }
        final var object = asObject(schema);
        if (object.getOrDefault("type", "").equals("string") && schemas.containsKey("enum")) {
            return false;
        }
        return !asObject(object.getOrDefault("properties", Map.of())).isEmpty();
    }

    private String anchorOf(final String name) {
        final var sanitized = name.replace('.', '_').replace('$', '_');
        return Character.isDigit(sanitized.charAt(0)) ? '_' + sanitized : sanitized;
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
            case "object" ->
                    "object" + ofNullable(schema.get("$id"))
                            .map(it -> " of type `" + it + "`")
                            .orElse("");
            default -> type;
        };
    }
}