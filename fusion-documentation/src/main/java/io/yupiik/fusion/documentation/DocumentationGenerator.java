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
import static java.util.Comparator.comparing;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

// prefer ConfigurationDocumentationGenerator since its name is more expressive, kept for backward compat
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
                        final var module = ofNullable(configuration.get("module"))
                                .orElseGet(() -> {
                                    // assume this form:
                                    // jar:file:/path/to/project/target/project-local-repo/group.id/artifact/version/group-version.jar!/META-INF/fusion/configuration/documentation.json
                                    if (url.getFile().endsWith("!/META-INF/fusion/configuration/documentation.json")) {
                                        final var versionSep = fileRef.indexOf('-');
                                        if (versionSep > 0) {
                                            return fileRef.substring(0, versionSep);
                                        }
                                        if (fileRef.endsWith(".jar")) {
                                            return fileRef.substring(0, fileRef.length() - ".jar".length());
                                        }
                                        // fallback on legacy case
                                    }
                                    // this will mainly work for fusion itself but unlikely for other libs, legacy fallback
                                    return fileRef.substring(fileRef.indexOf('-') + 1, fileRef.indexOf("-1"));
                                }); // fusion-$module-$version.jar
                        final var adoc = ofNullable(configuration.get("output"))
                                .map(Path::of)
                                .orElseGet(() -> sourceBase.resolve("content/_partials/generated/documentation." + module + ".adoc"));
                        final var params = findParameters(classes, roots);
                        Files.createDirectories(adoc.getParent());
                        Files.writeString(adoc, switch (configuration.getOrDefault("formatter", "default").toLowerCase(ROOT)) {
                            case "definitionlist" -> definitionList(params, includeEnv);
                            case "table" -> table(params, includeEnv);
                            default -> list(params, module, includeEnv);
                        });
                    }
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected List<Parameter> findParameters(final Map<?, ?> classes, final List<String> roots) {
        final var envPattern = Pattern.compile("[^A-Za-z0-9]");
        return roots.stream()
                .flatMap(rootName -> {
                    final var root = (Collection<?>) requireNonNull(classes.get(rootName), () -> "Missing configuration '" + rootName + "'");
                    return root.stream()
                            .map(it -> (Map<String, Object>) it)
                            .flatMap(item -> flatten(classes, item))
                            .map(it -> {
                                final var name = it.get("name").toString();
                                final var documentation = (String) it.get("documentation");
                                return new Parameter(
                                        name,
                                        documentation != null && !documentation.isBlank() && !documentation.endsWith(".") ?
                                                documentation + '.' : documentation,
                                        it.get("defaultValue"),
                                        Boolean.TRUE.equals(it.get("required")),
                                        envPattern.matcher(name.replace("$index", "INDEX")).replaceAll("_").toUpperCase(ROOT));
                            });
                })
                .sorted(comparing(Parameter::name))
                .toList();
    }

    protected String table(final List<Parameter> parameters, final boolean includeEnv) {
        return "[options=\"header\",cols=\"a,a,2a\"]\n" +
                "|===\n" +
                "|Name |" + (includeEnv ? "Env Variable |" : "") + "Description |Default\n" +
                "\n" +
                parameters.stream()
                        .map(e -> "| `" + e.name() + "` " + (e.required() ? "*" : "") + '\n' +
                                (includeEnv ? "| `" + e.envName() + "`\n" : "") +
                                "| " + e.documentation() + "\n" +
                                "| " + defaultFor(e.defaultValue()) +
                                "\n")
                        .collect(joining("\n")) +
                "|===\n";
    }

    protected String list(final List<Parameter> params, final String module, final boolean includeEnv) {
        return "= " + module + "\n" +
                "\n" +
                "== Configuration\n" +
                "\n" +
                params.stream()
                        .map(it -> "* `" + it.name() + "`" +
                                (includeEnv ? " (`" + it.envName() + "`)" : "") +
                                (it.required() ? "*" : "") +
                                ofNullable(it.defaultValue())
                                        .map(v -> " (default: `" + v + "`)")
                                        .orElse("") + ": " +
                                (it.documentation() == null || it.documentation().isBlank() ? "-." : it.documentation()))
                        .sorted()
                        .collect(joining("\n"));
    }

    protected String definitionList(final List<Parameter> parameters, final boolean includeEnv) {
        return parameters.stream()
                .map(e -> {
                    final var defaultValue = defaultFor(e.defaultValue());
                    return '`' + e.name() + '`' + (includeEnv ? " (env: `" + e.envName() + "`)" : "") + "::\n" +
                            e.documentation() +
                            (e.defaultValue() != null ? " Default: " + defaultValue + (!defaultValue.contains("\n") ? "." : "") : "") +
                            "\n";
                })
                .collect(joining());
    }

    protected String defaultFor(final Object defaultValue) {
        if (defaultValue == null) {
            return "-";
        }
        if (defaultValue instanceof String s) {
            if (s.startsWith("\"\"\"") && s.endsWith("\"\"\"")) {
                return formatDefault(s.substring(3, s.length() - 3).stripIndent());
            }
            if (s.startsWith("\"") && s.endsWith("\"") && s.indexOf('"', 1) == s.length() - 1) {
                return formatDefault(s.substring(1, s.length() - 1));
            }
        }
        return formatDefault(defaultValue.toString());
    }

    protected String formatDefault(final String value) {
        if (value.contains("\n")) {
            return "\n[source" + guessLanguage(value) + "]\n----\n" + value + "\n----\n";
        }
        return "`" + value + "`";
    }

    protected String guessLanguage(final String value) {
        if (value.contains("<html") || value.contains("<div>") || value.contains("<body") || value.contains("<p>")) {
            return ",html";
        }
        if (value.contains("\nconst ") || value.contains("\nlet ") || value.startsWith("const ") || value.startsWith("let ")) {
            return ",javascript";
        }
        if (value.contains("\nclass ") || value.startsWith("class ")) {
            return ",java";
        }
        if (value.contains("</") || value.contains("/>")) {
            return ",xml";
        }
        if (value.contains(":\n")) {
            return ",yaml";
        }
        return "";
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
                            try {
                                final var path = Path.of(it);
                                if (Files.exists(path)) {
                                    return path.toUri().toURL();
                                }
                            } catch (final RuntimeException re) {
                                // no-op
                            } catch (final MalformedURLException ex) {
                                throw new IllegalStateException(ex);
                            }
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList());
        }
        return Thread.currentThread().getContextClassLoader().getResources("META-INF/fusion/configuration/documentation.json");
    }

    @SuppressWarnings("unchecked")
    private Stream<Map<String, ?>> flatten(final Map<?, ?> classes, final Map<String, ?> item) {
        final var ref = item.get("ref");
        if (ref == null) {
            return Stream.of(item);
        }

        final var prefix = ofNullable(item.get("name")).map(Object::toString).orElse("");
        final var nested = (Collection<Map<String, Object>>) requireNonNull(classes.get(ref), () -> "Missing configuration '" + ref + "'");
        return nested.stream() // add prefix to nested configs
                .map(it -> Stream.concat(
                                Stream.of(Map.entry("name", prefix + "." + it.getOrDefault("name", "").toString())),
                                it.entrySet().stream().filter(i -> !"name".equals(i.getKey())))
                        .filter(Predicate.not(e -> e.getValue() == null))
                        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .flatMap(it -> flatten(classes, it));
    }

    public record Parameter(String name, String documentation,
                               Object defaultValue, boolean required,
                               String envName) {
    }
}
