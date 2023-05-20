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
package io.yupiik.fusion.framework.processor.internal.meta;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;

public record JsonSchema(
        String ref, String id,
        String type, Boolean nullable,
        String format, String pattern,
        Object additionalProperties,
        Map<String, JsonSchema> properties,
        JsonSchema items,
        String title, String description) implements GenericObjectJsonSerializationLike {
    public JsonSchema(final String ref, final String id,
                      final String type, final Boolean nullable,
                      final String format, final String pattern,
                      final Object additionalProperties,
                      final Map<String, JsonSchema> properties,
                      final JsonSchema items) { // backward compat
        this(ref, id, type, nullable, format, pattern, additionalProperties, properties, items, null, null);
    }

    @Override
    public Map<String, Object> asMap() {
        return Stream.of(
                        id == null ? null : entry("$id", id),
                        ref == null ? null : entry("$ref", ref),
                        title == null ? null : entry("title", title),
                        description == null ? null : entry("description", description),
                        type == null ? null : entry("type", type),
                        format == null ? null : entry("format", format),
                        pattern == null ? null : entry("pattern", pattern),
                        additionalProperties == null ? null : entry("additionalProperties", additionalProperties),
                        nullable == null ? null : entry("nullable", nullable),
                        items == null ? null : entry("items", items.asMap()),
                        properties == null ? null : entry("properties", properties.entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .collect(toMap(Map.Entry::getKey, e -> e.getValue().asMap(), (a, b) -> a, LinkedHashMap::new))))
                .filter(Objects::nonNull)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
