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
package io.yupiik.fusion.build.internal.agent;

import io.yupiik.fusion.documentation.DocumentationGenerator;
import io.yupiik.fusion.framework.handlebars.HandlebarsCompiler;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

/**
 * Keeps the repository agent instruction files (root {@code AGENTS.md}/{@code CLAUDE.md} and per module
 * {@code AGENTS.md}) in sync with the build.
 * <p>
 * Inputs are the maven poms (module list, names, descriptions, java release), the annotation processor
 * configuration metadata ({@code META-INF/fusion/configuration/documentation.json} found on the classpath)
 * and the handlebars templates in {@code src/main/resources/agents}.
 */
public class AgentsFileSynchronizer implements Runnable {
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final Path repositoryRoot;
    private final Path outputRoot;
    private final String version;
    private final boolean checkOnly;

    public AgentsFileSynchronizer(final Path repositoryRoot, final Path outputRoot,
                                  final String version, final boolean checkOnly) {
        this.repositoryRoot = repositoryRoot;
        this.outputRoot = outputRoot;
        this.version = version;
        this.checkOnly = checkOnly;
    }

    public static void main(final String... args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: AgentsFileSynchronizer <repositoryRoot> <version> [sync|check]");
        }
        final var root = Path.of(args[0]).toAbsolutePath().normalize();
        new AgentsFileSynchronizer(root, root, args[1], args.length > 2 && "check".equals(args[2])).run();
    }

    @Override
    public void run() {
        try {
            final var rootPom = readPom(repositoryRoot.resolve("pom.xml"));
            final var modules = collectModules(rootPom);
            final var configurations = readConfigurationTables();
            final var compiler = new HandlebarsCompiler();

            final var globalData = new HashMap<String, Object>();
            globalData.put("javaRelease", rootPom.properties().getOrDefault("maven.compiler.release", "17"));
            globalData.put("modules", modules.stream()
                    .map(module -> Map.<String, Object>of(
                            "artifactId", module.artifactId(),
                            "path", module.path(),
                            "name", module.name(),
                            "description", module.description()))
                    .toList());

            final var stale = new ArrayList<Path>();
            write(outputRoot.resolve("AGENTS.md"), banner("agents/root.md") + render(compiler, "agents/root.md", globalData), stale);
            write(outputRoot.resolve("CLAUDE.md"), banner("agents/claude.md") + render(compiler, "agents/claude.md", globalData), stale);
            for (final var module : modules) {
                final var data = new HashMap<String, Object>(globalData);
                data.put("artifactId", module.artifactId());
                data.put("path", module.path());
                data.put("name", module.name());
                data.put("description", module.description());
                data.put("configuration", configurations.getOrDefault(module.artifactId(), ""));
                if ("fusion-build-api".equals(module.artifactId())) {
                    data.put("annotations", buildApiAnnotationCatalog());
                }
                data.put("footer", render(compiler, "agents/module/_footer.md", data));

                final var template = ofNullable(getClass().getClassLoader().getResource("agents/module/" + module.artifactId() + ".md"))
                        .map(url -> "agents/module/" + module.artifactId() + ".md")
                        .orElseGet(() -> {
                            logger.warning(() -> "No template for module '" + module.artifactId() + "', " +
                                    "using agents/module/_default.md, please add agents/module/" + module.artifactId() + ".md");
                            return "agents/module/_default.md";
                        });
                write(outputRoot.resolve(module.path()).resolve("AGENTS.md"), banner(template) + render(compiler, template, data), stale);
            }

            if (checkOnly && !stale.isEmpty()) {
                throw new IllegalStateException(
                        "Stale agent files, refresh them with 'mvn install -pl fusion-build-internal': " + stale);
            }
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String banner(final String template) {
        return "<!--\n" +
                "  GENERATED FILE - DO NOT EDIT.\n" +
                "  Template: fusion-build-internal/src/main/resources/" + template + "\n" +
                "  Refresh with: mvn install -pl fusion-build-internal (any full build does it too).\n" +
                "-->\n";
    }

    private String render(final HandlebarsCompiler compiler, final String template, final Map<String, Object> data) throws IOException {
        try (final var in = getClass().getClassLoader().getResourceAsStream(template)) {
            if (in == null) {
                throw new IllegalStateException("Missing template '" + template + "'");
            }
            final var rendered = compiler
                    .compile(new HandlebarsCompiler.CompilationContext(new String(in.readAllBytes(), UTF_8)))
                    .render(data);
            return rendered.endsWith("\n") ? rendered : rendered + '\n';
        }
    }

    private void write(final Path target, final String content, final List<Path> stale) throws IOException {
        if (Files.exists(target) && content.equals(Files.readString(target))) {
            return;
        }
        if (checkOnly) {
            stale.add(target);
            return;
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, content);
        logger.info(() -> "Updated '" + target + "'");
    }

    private List<Module> collectModules(final Pom rootPom) {
        return rootPom.modules().stream()
                .flatMap(module -> collectModules(module, repositoryRoot))
                .toList();
    }

    private Stream<Module> collectModules(final String relativePath, final Path base) {
        final var directory = base.resolve(relativePath);
        final var pom = readPom(directory.resolve("pom.xml"));
        final var path = repositoryRoot.relativize(directory).toString().replace(File.separatorChar, '/');
        final var self = new Module(pom.artifactId(), path, pom.name(), pom.description() == null || pom.description().isBlank() ? pom.name() : pom.description());
        return Stream.concat(
                Stream.of(self),
                pom.modules().stream().flatMap(child -> collectModules(child, directory)));
    }

    private Pom readPom(final Path pom) {
        try {
            final var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            final var project = factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
            final var properties = new HashMap<String, String>();
            directChild(project, "properties").ifPresent(props -> {
                final var children = props.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element property) {
                        properties.put(property.getTagName(), property.getTextContent().strip());
                    }
                }
            });
            final var modules = new ArrayList<String>();
            directChild(project, "modules").ifPresent(mods -> {
                final var children = mods.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element module) {
                        modules.add(module.getTextContent().strip());
                    }
                }
            });
            return new Pom(
                    directChild(project, "artifactId").map(Element::getTextContent).map(String::strip).orElseThrow(),
                    directChild(project, "name").map(Element::getTextContent).map(String::strip).orElse(""),
                    directChild(project, "description").map(Element::getTextContent).map(String::strip).orElse(""),
                    modules, properties);
        } catch (final RuntimeException re) {
            throw re;
        } catch (final Exception e) {
            throw new IllegalStateException("Can't read '" + pom + "'", e);
        }
    }

    private Optional<Element> directChild(final Element parent, final String name) {
        final var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && name.equals(element.getTagName())) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private Map<String, String> readConfigurationTables() throws IOException {
        final var tables = new TreeMap<String, String>();
        final var parameters = new ConfigurationParameters();
        try (final var json = new JsonMapperImpl(List.of(), c -> Optional.empty())) {
            final var docs = getClass().getClassLoader().getResources("META-INF/fusion/configuration/documentation.json");
            while (docs.hasMoreElements()) {
                final var url = docs.nextElement();
                try (final var in = url.openStream()) {
                    if (!(json.fromBytes(Object.class, in.readAllBytes()) instanceof Map<?, ?> doc) ||
                            !(doc.get("classes") instanceof Map<?, ?> classes)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked") final var roots = ofNullable((List<String>) doc.get("roots")).orElse(List.of());
                    if (roots.isEmpty()) {
                        continue;
                    }
                    tables.put(moduleOf(url), asMarkdownTable(parameters.parameters(classes, roots)));
                }
            }
        }
        return tables;
    }

    private String moduleOf(final URL url) {
        final var file = url.getFile();
        final var target = file.indexOf("/target/classes/");
        if (target > 0) { // reactor case: .../<module>/target/classes/META-INF/...
            return file.substring(file.lastIndexOf('/', target - 1) + 1, target);
        }
        // jar case: .../<artifactId>-<version>.jar!/META-INF/...
        final var end = file.indexOf(".jar!/");
        if (end > 0) {
            final var jarName = file.substring(file.lastIndexOf('/', end) + 1, end);
            final var versionSuffix = "-" + version;
            return jarName.endsWith(versionSuffix) ? jarName.substring(0, jarName.length() - versionSuffix.length()) : jarName;
        }
        return file;
    }

    private String asMarkdownTable(final List<DocumentationGenerator.Parameter> parameters) {
        return "| Name | Env variable | Description | Default | Required |\n" +
                "|---|---|---|---|---|\n" +
                parameters.stream()
                        .map(parameter -> "| `" + cell(parameter.name()) + "` " +
                                "| `" + cell(parameter.envName()) + "` " +
                                "| " + cell(ofNullable(parameter.documentation()).orElse("-")) + " " +
                                "| " + (parameter.defaultValue() == null ? "-" : "`" + cell(String.valueOf(parameter.defaultValue())) + "`") + " " +
                                "| " + (parameter.required() ? "yes" : "no") + " |")
                        .collect(joining("\n"));
    }

    private String cell(final String value) {
        return value.replace("|", "\\|").replace("\r\n", "\n").replace("\n", "<br>");
    }

    private String buildApiAnnotationCatalog() throws IOException {
        final var basePackage = "io/yupiik/fusion/framework/build/api/";
        final var marker = basePackage + "scanning/Bean.class";
        final var url = getClass().getClassLoader().getResource(marker);
        if (url == null) {
            throw new IllegalStateException("Can't locate fusion-build-api on the classpath");
        }
        final var byPackage = new TreeMap<String, TreeSet<String>>();
        if ("jar".equals(url.getProtocol())) {
            final var jar = ((JarURLConnection) url.openConnection()).getJarFile();
            jar.stream()
                    .map(java.util.jar.JarEntry::getName)
                    .filter(name -> name.startsWith(basePackage) && name.endsWith(".class"))
                    .forEach(name -> addToCatalog(byPackage, name.substring(basePackage.length())));
        } else {
            final var root = Path.of(url.getPath().substring(0, url.getPath().length() - marker.length())).resolve(basePackage);
            try (final var walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .map(path -> root.relativize(path).toString().replace(File.separatorChar, '/'))
                        .filter(name -> name.endsWith(".class"))
                        .forEach(name -> addToCatalog(byPackage, name));
            }
        }
        return byPackage.entrySet().stream()
                .map(entry -> "- `" + entry.getKey() + "`: " + entry.getValue().stream()
                        .map(type -> "`" + (isAnnotation("io.yupiik.fusion.framework.build.api." + entry.getKey() + "." + type) ? "@" : "") + type + "`")
                        .collect(joining(", ")))
                .collect(joining("\n"));
    }

    private boolean isAnnotation(final String fqn) {
        try {
            return getClass().getClassLoader().loadClass(fqn).isAnnotation();
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private void addToCatalog(final Map<String, TreeSet<String>> byPackage, final String relativeClassName) {
        final var className = relativeClassName.substring(0, relativeClassName.length() - ".class".length());
        if (className.contains("$") || className.endsWith("package-info") || !className.contains("/")) {
            return;
        }
        final var sep = className.lastIndexOf('/');
        byPackage.computeIfAbsent(className.substring(0, sep).replace('/', '.'), k -> new TreeSet<>())
                .add(className.substring(sep + 1));
    }

    private record Module(String artifactId, String path, String name, String description) {
    }

    private record Pom(String artifactId, String name, String description,
                       List<String> modules, Map<String, String> properties) {
    }

    // just opens the protected extraction logic of the base generator
    private static class ConfigurationParameters extends DocumentationGenerator {
        private ConfigurationParameters() {
            super(Path.of("."), Map.of());
        }

        private List<Parameter> parameters(final Map<?, ?> classes, final List<String> roots) {
            return findParameters(classes, roots);
        }
    }
}
