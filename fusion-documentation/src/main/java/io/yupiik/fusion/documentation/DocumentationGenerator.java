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
package io.yupiik.fusion.documentation;

import io.yupiik.fusion.json.internal.JsonMapperImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

public class DocumentationGenerator implements Runnable {
    private final Path sourceBase;

    public DocumentationGenerator(final Path sourceBase) {
        this.sourceBase = sourceBase;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        try (final var json = new JsonMapperImpl(List.of(), c -> Optional.empty())) {
            final var docs = Thread.currentThread().getContextClassLoader().getResources("META-INF/fusion/configuration/documentation.json");
            while (docs.hasMoreElements()) {
                final var url = docs.nextElement();
                try (final var in = url.openStream()) {
                    final var doc = (Map<String, Object>) json.fromBytes(Object.class, in.readAllBytes());
                    if (doc.get("classes") instanceof Map<?, ?> classes) {
                        final var roots = ofNullable((List<String>) doc.get("roots")).orElse(List.of());

                        var file = url.getFile().replace("!/META-INF/fusion/configuration/documentation.json", "");
                        file = file.substring(Math.max(file.lastIndexOf('/'), file.lastIndexOf(File.separator)) + 1);
                        final var module = file.substring(file.indexOf('-') + 1, file.indexOf("-1")); // fusion-$module-$version.jar
                        final var adoc = sourceBase.resolve("content/_partials/generated/documentation." + module + ".adoc");
                        Files.createDirectories(adoc.getParent());
                        Files.writeString(adoc, "= " + module + "\n" +
                                "\n" +
                                "== Configuration\n" +
                                "\n" +
                                roots.stream()
                                        .flatMap(rootName -> {
                                            final var root = (Collection<?>) requireNonNull(classes.get(rootName), () -> "Missing configuration '" + rootName + "'");
                                            return root.stream()
                                                    .map(it -> (Map<String, Object>) it)
                                                    .flatMap(item -> flatten(classes, item))
                                                    .map(it -> {
                                                        final var name = it.get("name").toString();
                                                        return "* `" + name + "`" +
                                                                (Boolean.TRUE.equals(it.get("required")) ? "*" : "") +
                                                                ofNullable(it.get("defaultValue"))
                                                                        .map(v -> " (default: `" + v + "`)")
                                                                        .orElse("") + ": " +
                                                                it.getOrDefault("documentation", "-") + ".";
                                                    });
                                        })
                                        .sorted()
                                        .collect(joining("\n")));
                    }
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Stream<Map<String, Object>> flatten(final Map<?, ?> classes, final Map<String, Object> item) {
        final var ref = item.get("ref");
        if (ref == null) {
            return Stream.of(item);
        }

        final var prefix = item.getOrDefault("name", "").toString();
        final var nested = (Collection<Map<String, Object>>) requireNonNull(classes.get(ref), () -> "Missing configuration '" + ref + "'");
        return nested.stream() // add prefix to nested configs
                .map(it -> Stream.concat(
                                Stream.of(Map.entry("name", prefix + "." + it.getOrDefault("name", "").toString())),
                                it.entrySet().stream().filter(i -> !"name".equals(i.getKey())))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .map(it -> (Map<String, Object>) it)
                .flatMap(it -> flatten(classes, it));
    }
}
