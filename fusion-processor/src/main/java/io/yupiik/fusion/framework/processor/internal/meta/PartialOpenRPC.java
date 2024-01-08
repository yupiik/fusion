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
package io.yupiik.fusion.framework.processor.internal.meta;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;

// partial structure - what we use, should be aggregated to build a real instance
public record PartialOpenRPC(Map<String, JsonSchema> schemas,
                             Map<String, Method> methods) implements GenericObjectJsonSerializationLike {
    @Override
    public Map<String, Object> asMap() {
        return Stream.of(
                        // /components/schemas
                        schemas == null ?
                                null :
                                entry("schemas", (Object) schemas.entrySet().stream()
                                        .map(it -> entry(it.getKey(), it.getValue().asMap()))
                                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new))),
                        methods == null ?
                                null :
                                entry("methods", (Object) methods.entrySet().stream()
                                        .map(it -> entry(it.getKey(), it.getValue().asMap()))
                                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new))))
                .filter(Objects::nonNull)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    public record Value(String name, String description, Boolean required, Boolean deprecated, JsonSchema schema) {
        public Map<String, Object> asMap() {
            return Stream.of(
                            name == null ? null : entry("name", (Object) name),
                            description == null ? null : entry("description", (Object) description),
                            required == null ? null : entry("required", (Object) required),
                            deprecated == null ? null : entry("deprecated", (Object) deprecated),
                            schema == null ? null : entry("schema", (Object) schema.asMap()))
                    .filter(Objects::nonNull)
                    .sorted(Map.Entry.comparingByKey())
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }
    }

    public record ErrorValue(int code, String message, Object data) {
        public Map<String, Object> asMap() {
            return Stream.of(
                            entry("code", (Object) code),
                            message == null ? null : entry("message", (Object) message),
                            data == null ? null : entry("data", data))
                    .filter(Objects::nonNull)
                    .sorted(Map.Entry.comparingByKey())
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }
    }

    public record Method(String name, String summary, String description, Boolean deprecated, String paramStructure,
                         Collection<Value> params, Value result, Collection<ErrorValue> errors) {
        public Map<String, Object> asMap() {
            return Stream.of(
                            name == null ? null : entry("name", (Object) name),
                            summary == null ? null : entry("summary", (Object) summary),
                            description == null ? null : entry("description", (Object) description),
                            deprecated == null ? null : entry("deprecated", (Object) deprecated),
                            paramStructure == null ? null : entry("paramStructure", (Object) paramStructure),
                            params == null ? null : entry("params", (Object) params.stream().map(Value::asMap).toList()),
                            result == null ? null : entry("result", (Object) result.asMap()),
                            errors == null ? null : entry("errors", (Object) errors.stream().map(ErrorValue::asMap).toList()))
                    .filter(Objects::nonNull)
                    .sorted(Map.Entry.comparingByKey())
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        }
    }
}
