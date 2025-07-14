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
package io.yupiik.fusion.framework.processor.internal.generator;

import io.yupiik.fusion.framework.processor.internal.GeneratedJsonSchema;
import io.yupiik.fusion.framework.processor.internal.json.JsonMapperFacade;
import io.yupiik.fusion.json.internal.formatter.SimplePrettyFormatter;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

public class CrdDescriptorGenerator implements Supplier<String> {
    private final Map<String, Object> crd;
    private final Map<String, GeneratedJsonSchema> schemas;
    private final JsonMapperFacade json;
    private final Elements elementUtils;
    private final TypeMirror object;
    private final Types types;

    public CrdDescriptorGenerator(final Elements elementUtils,
                                  final Types types,
                                  final Map<String, Object> crd, final Map<String, GeneratedJsonSchema> jsonSchemas,
                                  final TypeMirror object) {
        this.elementUtils = elementUtils;
        this.types = types;
        this.crd = crd;
        this.schemas = jsonSchemas;
        this.object = object;
        this.json = new JsonMapperFacade();
    }

    @Override
    public String get() {
        final var singular = crd.get("name").toString().toLowerCase(Locale.ROOT);
        final var plural = singular + "s";

        final var jsonSchema = new TreeMap<String, Object>();
        jsonSchema.put("spec", findJsonSchema(crd.get("spec")));

        // todo: handle scale
        final var subResources = new TreeMap<String, Map<String, Object>>();
        if (crd.get("status") instanceof TypeMirror tm && !types.isSameType(object, tm)) {
            subResources.put("status", Map.of());
            jsonSchema.put("status", findJsonSchema(tm));
        }

        final var version = new TreeMap<>(Map.of(
                "name", crd.get("version"),
                "served", true,
                "storage", true,
                "schema", new TreeMap<String, Object>(Map.of(
                        "openAPIV3Schema", jsonSchema,
                        "subresources", subResources
                ))
        ));

        if (crd.get("additionalPrinterColumns") instanceof List<?> l && !l.isEmpty()) {
            version.put("additionalPrinterColumns", l.stream()
                    .map(it -> {
                        final var values = elementUtils.getElementValuesWithDefaults((AnnotationMirror) it).entrySet().stream()
                                .collect(toMap(
                                        i -> i.getKey().getSimpleName().toString(),
                                        i -> i.getValue().getValue()));
                        final var map = new TreeMap<>(Map.of(
                                "name", values.get("name").toString(),
                                "type", values.get("type").toString(),
                                "jsonPath", values.get("jsonPath").toString()));
                        final var priority = values.get("priority");
                        if (priority instanceof Number n && n.intValue() > 0) {
                            map.put("priority", Integer.toString(n.intValue()));
                        }
                        return map;
                    })
                    .toList());
        }
        if (crd.get("selectableFields") instanceof List<?> l && !l.isEmpty()) {
            version.put("selectableFields", l.stream()
                    .map(it -> it instanceof AnnotationValue v ? v.getValue() : it.toString())
                    .map(it -> Map.of("jsonPath", it))
                    .toList());
        }

        final var names = new TreeMap<>(Map.of(
                "kind", crd.get("name"),
                "singular", singular,
                "plural", plural
        ));

        final var shortNames = crd.get("shortNames");
        if (shortNames instanceof List<?> l && !l.isEmpty()) {
            names.put("shortNames", l.stream()
                    .map(it -> it instanceof AnnotationValue av ? av.getValue() : it.toString())
                    .toList());
        }

        final var schema = new TreeMap<>(Map.of(
                "apiVersion", "apiextensions.k8s.io/v1",
                "kind", "CustomResourceDefinition",
                "metadata", Map.of("name", plural + "." + crd.get("group")),
                "spec", new TreeMap<String, Object>(Map.of(
                        "scope", Boolean.TRUE.equals(crd.get("namespaced")) ? "Namespaced" : "Cluster",
                        "group", crd.get("group"),
                        "names", names,
                        // todo: enhance to make @CustomResourceDefinition repeatable
                        "versions", List.of(version)))));
        final var inline = json.write(schema);
        try {
            return new SimplePrettyFormatter(json.getMapper()).apply(inline);
        } catch (final Error | Exception e) {
            return inline;
        }
    }

    // todo: make this more effective even if not important for this part
    private Map<String, Object> findJsonSchema(final Object type) {
        var name = type instanceof TypeMirror tm ? tm.toString() : ((Class<?>) type).getName();
        name = name.replace('$', '.');
        return doFindSchema(name);
    }

    private Map<String, Object> doFindSchema(final String name) {
        final var jsonSchema = requireNonNull(schemas.get(name), "No JSON-Schema for type '" + name + "'");
        return inlineSchema(json.read(jsonSchema.content() != null ? json.write(jsonSchema.content().asMap()) : jsonSchema.raw()));
    }

    // drop $id and replace $ref
    @SuppressWarnings("unchecked")
    private Map<String, Object> inlineSchema(final Map<String, Object> schema) {
        return schema.entrySet().stream()
                .filter(it -> !"$id".equals(it.getKey()))
                .flatMap(e -> "$ref".equals(e.getKey()) && e.getValue().toString().startsWith("#/schemas/") ?
                        doFindSchema(e.getValue().toString().substring("#/schemas/".length())).entrySet().stream() :
                        Stream.of(entry(e.getKey(), e.getValue() instanceof Map<?, ?> s ? inlineSchema((Map<String, Object>) s) : e.getValue())))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }
}
