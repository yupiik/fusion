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
package io.yupiik.fusion.framework.processor.internal;

import io.yupiik.fusion.framework.processor.internal.generator.JsonCodecGenerator;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;

/** Compiler-side discovery and external incremental state, independent of optional JSON schemas. */
public final class JsonModelIndex {
    private final ProcessingEnvironment environment;
    private final CompilerSource sources;
    private final Path output;
    private final Path cache;
    private final TreeMap<String, String> models = new TreeMap<>();
    private final Set<String> touched = new HashSet<>();
    private final Set<String> currentModels = new HashSet<>();
    private final Map<String, TypeElement> resolvedTypes = new HashMap<>();

    public JsonModelIndex(final ProcessingEnvironment environment, final Path workdir, final Path output) {
        this.environment = environment;
        this.sources = CompilerSource.of(environment);
        this.output = output;
        this.cache = workdir == null ? null : workdir.resolve("json-models-v1.properties");
        try {
            if (cache != null && Files.exists(cache)) {
                final var properties = new Properties();
                try (final var in = Files.newBufferedReader(cache, UTF_8)) {
                    properties.load(in);
                }
                properties.forEach((key, value) -> models.put(key.toString(), value.toString()));
            }
            prune();
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot load JSON model index; perform a full clean rebuild", e);
        }
    }

    public Set<String> discover() {
        final var result = new HashSet<>(models.keySet());
        new HashSet<>(result).forEach(name -> sourceNames(name).forEach(result::add));
        return result;
    }

    /** Uses the compiler-selected type, including source shadowing and a separate processor path. */
    public boolean isDependencyModel(final String name) {
        final var type = indexedType(name);
        if (type == null) {
            return false;
        }
        final var binary = environment.getElementUtils().getBinaryName(type).toString();
        final boolean local = !(environment instanceof ModuleProcessingEnvironment scoped) || scoped.owns(type);
        if (local && (belongsTo(type, touched) || sources.uri(type).isPresent())) {
            // Never let a stale dependency codec turn an unannotated source replacement into a model.
            return models.containsKey(binary);
        }
        if (local && output != null && Files.exists(output.resolve(binary.replace('.', '/') + ".class"))) {
            // Deleted local sources must not be resurrected through obsolete class output.
            return models.containsKey(binary);
        }
        if (type.getAnnotationMirrors().stream().anyMatch(it -> it.getAnnotationType().toString()
                .equals("io.yupiik.fusion.framework.build.api.json.JsonModel"))) {
            return true;
        }
        if (type.getRecordComponents().stream().anyMatch(this::hasJsonProperty) ||
                type.getEnclosedElements().stream()
                        .filter(ExecutableElement.class::isInstance)
                        .map(ExecutableElement.class::cast)
                        .filter(it -> it.getKind() == CONSTRUCTOR)
                        .flatMap(it -> it.getParameters().stream()).anyMatch(this::hasJsonProperty)) {
            return true;
        }
        // Published SOURCE-retained models can have a legacy named codec.
        // Resolve it on the compilation classpath instead of eagerly instantiating dependency modules.
        return indexedType(binary + JsonCodecGenerator.SUFFIX) != null;
    }

    private boolean hasJsonProperty(final Element element) {
        return element.getAnnotationMirrors().stream().map(it -> it.getAnnotationType().toString())
                .anyMatch(it -> it.equals("io.yupiik.fusion.framework.build.api.json.JsonProperty") ||
                        it.equals("io.yupiik.fusion.framework.build.api.json.JsonOthers"));
    }

    private Stream<String> sourceNames(final String name) {
        final var type = indexedType(name);
        return type == null ? Stream.of(name) : Stream.of(name, type.getQualifiedName().toString());
    }

    // Retain positive resolutions only; literal dollars are not necessarily nesting separators.
    private TypeElement indexedType(final String name) {
        final var resolved = resolvedTypes.get(name);
        if (resolved != null) {
            return resolved;
        }
        final var direct = environment.getElementUtils().getTypeElement(name);
        if (direct != null) {
            resolvedTypes.put(name, direct);
            return direct;
        }
        for (int split = name.lastIndexOf('$'); split >= 0; split = name.lastIndexOf('$', split - 1)) {
            final var enclosing = indexedType(name.substring(0, split));
            if (enclosing != null) {
                final var simple = name.substring(split + 1);
                for (final var member : enclosing.getEnclosedElements()) {
                    if (member instanceof TypeElement nested && nested.getSimpleName().contentEquals(simple)) {
                        resolvedTypes.put(name, nested);
                        return nested;
                    }
                }
            }
        }
        // Do not cache misses: another processor can introduce the type in a later round.
        return null;
    }

    public void touch(final Element element) {
        if (element instanceof TypeElement type) {
            final var origin = environment.getElementUtils().getBinaryName(type).toString();
            if (touched.add(origin)) {
                final var source = sources.uri(type).orElse(null);
                // A binary-name prefix is not ownership: a top-level type can contain a literal '$'.
                models.entrySet().removeIf(it -> (source != null && source.equals(it.getValue())) ||
                        it.getKey().equals(origin) || belongsTo(indexedType(it.getKey()), Set.of(origin)));
            }
        }
    }

    private boolean belongsTo(final TypeElement type, final Set<String> origins) {
        for (Element current = type; current instanceof TypeElement enclosing; current = enclosing.getEnclosingElement()) {
            if (origins.contains(environment.getElementUtils().getBinaryName(enclosing).toString())) {
                return true;
            }
        }
        return false;
    }

    public void add(final Element element) {
        if (element instanceof TypeElement type) {
            final var binary = environment.getElementUtils().getBinaryName(type).toString();
            currentModels.add(binary);
            models.put(binary,
                    sources.uri(type).orElse(""));
        }
    }

    private void prune() {
        models.entrySet().removeIf(it -> !it.getValue().isEmpty() && it.getValue().startsWith("file:") &&
                !Files.exists(Path.of(URI.create(it.getValue()))));
        models.keySet().removeIf(it -> output != null &&
                !currentModels.contains(it) &&
                !Files.exists(output.resolve(it.replace('.', '/') + ".class")));
    }

    public void emit() throws IOException {
        prune();
        for (final var model : models.entrySet()) {
            if (!currentModels.contains(model.getKey()) && !model.getValue().startsWith("file:")) {
                throw new IllegalStateException("Cannot verify source deletion for cached JSON model " + model.getKey() +
                        "; this compiler requires a full clean rebuild for partial recompilation");
            }
        }
        if (cache != null) {
            Files.createDirectories(cache.getParent());
            final var properties = new Properties();
            properties.putAll(models);
            try (final var out = Files.newBufferedWriter(cache, UTF_8)) {
                properties.store(out, "Fusion JSON model index v1 (build-only)");
            }
        }
    }
}
