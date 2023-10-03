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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Collections.enumeration;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

public class DocumentationGenerator implements Runnable {
    private final Path sourceBase;
    private final Map<String, String> configuration;

    public DocumentationGenerator(final Path sourceBase, final Map<String, String> configuration) {
        this.sourceBase = sourceBase;
        this.configuration = configuration == null ? Map.of() : configuration;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        final boolean includeEnv = Boolean.parseBoolean(configuration.get("includeEnvironmentNames"));
        final var envPattern = includeEnv ? Pattern.compile("[^A-Za-z0-9]") : null;

        try (final var json = new JsonMapperImpl(List.of(), c -> Optional.empty())) {
            final var docs = findUrls();
            while (docs.hasMoreElements()) {
                final var url = docs.nextElement();
                try (final var in = url.openStream()) {
                    final var doc = (Map<String, Object>) json.fromBytes(Object.class, in.readAllBytes());
                    if (doc.get("classes") instanceof Map<?, ?> classes) {
                        final var roots = ofNullable((List<String>) doc.get("roots")).orElse(List.of());

                        var file = url.getFile().replace("!/META-INF/fusion/configuration/documentation.json", "");
                        file = file.substring(Math.max(file.lastIndexOf('/'), file.lastIndexOf(File.separator)) + 1);

                        final var fileRef = file;
                        final var module = ofNullable(configuration.get("module")).orElseGet(() -> fileRef.substring(fileRef.indexOf('-') + 1, fileRef.indexOf("-1"))); // fusion-$module-$version.jar
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
                                                                (includeEnv ? " (`" + envPattern.matcher(name).replaceAll("_") + "`)" : "") +
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

    private Enumeration<URL> findUrls() throws IOException {
        final var paths = configuration.get("urls");
        if (paths != null) {
            return enumeration(Stream.of(paths.split(","))
                    .map(String::strip)
                    .filter(Predicate.not(String::isBlank))
                    .map(it -> {
                        try {
                            return new URL(it);
                        } catch (final MalformedURLException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList());
        }
        return Thread.currentThread().getContextClassLoader().getResources("META-INF/fusion/configuration/documentation.json");
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
                        .collect(toMap(Map.Entry::getKey, i -> i.getValue() == null ? Map.of() : i.getValue())))
                .flatMap(it -> flatten(classes, it));
    }
}
