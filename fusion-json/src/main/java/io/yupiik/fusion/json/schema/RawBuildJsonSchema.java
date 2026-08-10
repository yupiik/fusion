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
package io.yupiik.fusion.json.schema;

import java.util.List;
import java.util.Map;

/**
 * Typed view of a single JSON schema emitted by the Fusion annotation processor (i.e. the lightweight
 * Draft-07/OpenAPI hybrid flavor). The processor produces one such schema per {@code @JsonModel} record, tagged
 * with its {@code $id}.
 * <p>
 * This record is the compile-free counterpart of the processor's {@code io.yupiik.fusion.framework.processor...JsonSchema}
 * metadata: it exists directly in the runtime module so {@code META-INF/fusion/json/schemas.json} bundles can be
 * deserialized and then upgraded to Draft 2020-12 via {@link JsonSchemaService} all without any reflection.
 * <p>
 * The {@code additionalProperties} element is kept as an {@link Object} because it can be either a boolean
 * ({@code true}/{@code false}) or a nested schema object. {@code enum} accepts any string-enum values; non-string
 * enum members (rare) are represented as their {@link String} form.
 *
 * @see JsonSchemaService#toJsonSchema202012(RawBuildJsonSchema)
 */
public record RawBuildJsonSchema(
        String id,
        String ref,
        String type,
        Boolean nullable,
        String format,
        String pattern,
        Object additionalProperties,
        Map<String, RawBuildJsonSchema> properties,
        RawBuildJsonSchema items,
        String title,
        String description,
        List<String> enumeration,
        List<String> required) {

    /**
     * Builds a typed schema from a generic JSON tree (as produced by a {@link io.yupiik.fusion.json.JsonMapper}),
     * honoring the legacy keyword names ({@code $ref}, {@code $id}, {@code enum}, ...).
     *
     * @param tree the generic JSON tree.
     * @return the typed schema.
     */
    @SuppressWarnings("unchecked")
    public static RawBuildJsonSchema fromJsonTree(final Object tree) {
        if (!(tree instanceof Map<?, ?> map)) {
            return null;
        }
        final var raw = (Map<String, Object>) map;
        final var properties = (Map<String, Object>) raw.get("properties");
        final var required = (List<Object>) raw.get("required");
        return new RawBuildJsonSchema(
                ofString(raw.get("$id")),
                ofString(raw.get("$ref")),
                ofString(raw.get("type")),
                raw.get("nullable") instanceof Boolean b ? b : null,
                ofString(raw.get("format")),
                ofString(raw.get("pattern")),
                raw.get("additionalProperties"),
                properties == null ? null : properties.entrySet().stream()
                        .collect(java.util.LinkedHashMap::new,
                                (m, e) -> m.put(e.getKey(), fromJsonTree(e.getValue())),
                                java.util.Map::putAll),
                fromJsonTree(raw.get("items")),
                ofString(raw.get("title")),
                ofString(raw.get("description")),
                enumValues(raw.get("enum")),
                required == null ? null : required.stream().map(String::valueOf).toList());
    }

    private static String ofString(final Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumValues(final Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        return (List<String>) (List<?>) list;
    }
}