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

import io.yupiik.fusion.framework.processor.internal.CompilerSource;
import io.yupiik.fusion.framework.processor.internal.ModuleProcessingEnvironment;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;

/** Owns compact output and the versioned, origin-aware incremental source-fragment cache. */
public final class BindingEmitter {
    private static final int VERSION = 4;
    private static final int FACTORY_CHARS = 8_000;
    private static final int SHARD_SIZE = 32;
    private static final int SHARD_CHARS = 48_000;
    private static final Pattern IDENTIFIER = Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*");
    private static final Pattern HELPER_REFERENCE = Pattern.compile("\\$(?:callback|helper)\\{([0-9]+)}");
    private static final Pattern FACTORY_REFERENCE = Pattern.compile("\\$factory\\{([^}]+)}");
    private final ProcessingEnvironment environment;
    private final CompilerSource sources;
    private final Path cache;
    private final Path output;
    private final Path marker;
    private final Path inventory;
    private final Path pendingUpdate;
    private String namespace;
    private final String coordinationNamespace = UUID.randomUUID().toString().replace("-", "");
    private final Map<String, Entry> entries = new TreeMap<>();
    private final Map<String, Set<String>> origins = new TreeMap<>();
    private final Map<String, String> factories = new TreeMap<>();
    private final Map<String, Map<String, String>> ownerExpressions = new TreeMap<>();
    private final Set<String> expanding = new HashSet<>();
    private final Map<String, String> descriptorRegistrations = new TreeMap<>();
    private final StringBuilder moduleHelpers = new StringBuilder();
    private final Set<String> previousIds = new HashSet<>();
    private final Set<String> touched = new HashSet<>();
    private final Set<String> touchedSources = new HashSet<>();
    private final Set<String> requiredOrigins = new HashSet<>();
    private final Set<String> previousHolders = new HashSet<>();
    private final List<JavaFileObject> roundSources = new ArrayList<>();
    private final Set<String> roundPackages = new HashSet<>();
    private boolean emitted;
    private int round;
    private int moduleSize;
    private int nestedOrdinal;
    private final boolean previousArtifacts;
    private boolean missingState;

    public BindingEmitter(final ProcessingEnvironment environment, final Path workdir, final Path output) {
        this.environment = environment;
        this.sources = CompilerSource.of(environment);
        this.output = output;
        this.marker = output == null ? (workdir == null ? null : workdir.resolve("compact-origins-v1")) :
                output.resolveSibling(".fusion-" + output.getFileName()).resolve("compact-origins-v1");
        this.inventory = marker == null ? null : marker.resolveSibling("compact-holders-v1");
        this.pendingUpdate = marker == null ? null : marker.resolveSibling("compact-update-in-progress-v1");
        if (pendingUpdate != null && Files.exists(pendingUpdate)) {
            throw new IllegalStateException("Incomplete compact output update; perform a full clean rebuild (including class output and work state)");
        }
        this.cache = workdir == null ? null : workdir.resolve("compact-v1.bin");
        if (cache != null && output != null && cache.toAbsolutePath().normalize().startsWith(output.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("fusion.workdir must be outside packaged class output");
        }
        try {
            if (inventory != null && Files.exists(inventory)) {
                previousHolders.addAll(Files.readAllLines(inventory, UTF_8));
            } else if (output != null && Files.isDirectory(output)) {
                // Lost-state detection never depends on a packaged service index.
                try (final var files = Files.walk(output)) {
                    files.filter(Files::isRegularFile).filter(it -> {
                        final var name = it.getFileName().toString();
                        return name.endsWith(".class") && name.startsWith("FusionBindings_");
                    }).map(it -> output.relativize(it).toString().replace('\\', '/').replace('/', '.'))
                            .map(it -> it.substring(0, it.length() - ".class".length())).forEach(previousHolders::add);
                }
            }
            if (marker != null && Files.exists(marker)) {
                requiredOrigins.addAll(Files.readAllLines(marker, UTF_8));
            }
            if (cache != null && Files.exists(cache)) {
                load();
                previousIds.addAll(entries.keySet());
                entries.values().forEach(it -> requiredOrigins.remove(it.origin()));
            }
            // Inline-only modules have no holders: their external module inventory must also guard lost fragments.
            final var moduleInventory = marker == null ? null : marker.resolveSibling("generated-module");
            missingState = marker != null && !Files.exists(marker) && (cache == null || !Files.exists(cache)) &&
                    (!previousHolders.isEmpty() || (Files.exists(moduleInventory) && !Files.readString(moduleInventory, UTF_8).isBlank()));
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot load compact fragments; perform a full clean rebuild", e);
        }
        previousArtifacts = !previousIds.isEmpty() || !requiredOrigins.isEmpty();
    }

    public void touch(final Element element) {
        if (!(element instanceof TypeElement type)) {
            return;
        }
        final var origin = environment.getElementUtils().getBinaryName(type).toString();
        if (touched.add(origin)) {
            final var source = sources.uri(type).orElse(null);
            final var replaced = new HashSet<String>();
            replaced.add(origin);
            if (source != null && touchedSources.add(source)) {
                // Several top-level declarations can share one compilation unit.
                entries.values().stream().filter(it -> source.equals(it.source()))
                        .map(Entry::origin).forEach(replaced::add);
            }
            for (final var owner : replaced) {
                final var ids = origins.remove(owner);
                if (ids != null) {
                    ids.forEach(entries::remove);
                }
            }
        }
    }

    public void add(final Element element, final BaseGenerator.GeneratedClass generated) {
        if (emitted) {
            throw new IllegalStateException("Compact artifacts discovered after holder emission: " + generated.name());
        }
        var root = element;
        while (root.getEnclosingElement() instanceof TypeElement) {
            root = root.getEnclosingElement();
        }
        // ECJ can contribute annotated source-path types that are absent from the round's roots.
        touch(root);
        final var origin = environment.getElementUtils().getBinaryName((TypeElement) root).toString();
        final var source = sources.uri(root).orElse("");
        for (final var artifact : generated.artifacts()) {
            final var entry = new Entry(origin, source, artifact.binding());
            final var previous = entries.putIfAbsent(artifact.stableId(), entry);
            if (previous != null && !previous.equals(entry)) {
                throw new IllegalArgumentException("Duplicate compact artifact identity: " + artifact.stableId());
            }
            origins.computeIfAbsent(origin, ignored -> new HashSet<>()).add(artifact.stableId());
        }
    }

    public boolean contains(final String name) {
        return entries.containsKey(name);
    }

    /**
     * Requests another processing round without producing a runtime class.
     * Hack to avoid warnings and have more guarantee for last generation round.
     */
    public void defer() {
        if (emitted) {
            return;
        }
        final var pack = "io.yupiik.fusion.generated.compact_" + coordinationNamespace + ".round" + round++;
        roundPackages.add(pack);
        try {
            final var source = environment.getFiler().createSourceFile(pack + ".FusionRound");
            roundSources.add(source);
            try (final var out = source.openWriter()) {
                out.write("package " + pack + ";\n");
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot coordinate compact generation rounds: " + e.getMessage(), e);
        }
    }

    /** Removes coordination-only sources after javac has consumed their processing rounds. */
    public void deleteRoundSources() {
        for (final var source : roundSources) {
            try {
                if ("file".equals(source.toUri().getScheme())) {
                    final var path = Path.of(source.toUri());
                    Files.deleteIfExists(path);
                    deleteEmptyRoundDirectory(path.getParent());
                    deleteEmptyRoundDirectory(path.getParent().getParent());
                } else {
                    source.delete(); // In-memory or compiler-specific outputs can own their cleanup.
                }

            } catch (final IOException e) {
                throw new IllegalStateException("Cannot remove processing-round source " + source.toUri(), e);
            }
        }
        roundSources.clear();
    }

    public boolean isRoundSource(final Element element) {
        // Compilers can expose package-only units differently; ownership is the exact emitted package.
        final var pack = environment.getElementUtils().getPackageOf(element);
        return pack != null && roundPackages.contains(pack.getQualifiedName().toString());
    }

    private void deleteEmptyRoundDirectory(final Path directory) throws IOException {
        try {
            Files.deleteIfExists(directory);
        } catch (final DirectoryNotEmptyException nonEmpty) {
            // Other rounds or compiler outputs still own this directory.
        }
    }

    public boolean wasBinding(final String name) {
        return previousIds.contains(name);
    }

    public boolean hadArtifacts() {
        return previousArtifacts;
    }

    public String factory(final String name) {
        return factories.getOrDefault(name, "new " + name + "()");
    }

    /** Direct expressions or sibling stream calls, retaining the inventory's stable-ID ordering. */
    public List<String> registrationCalls(final List<String> names) {
        return names.stream().map(name -> descriptorRegistrations.getOrDefault(name, "builder.add(" + factory(name) + ");"))
                .filter(it -> !it.isEmpty()).toList();
    }

    public String moduleHelpers() {
        return resolveReferences(moduleHelpers.toString());
    }

    public boolean hasModuleConstruction() {
        return !moduleHelpers.isEmpty();
    }

    public Set<String> registrations(final String kind) {
        final var result = new HashSet<String>();
        entries.forEach((name, entry) -> {
            if (kind.equals(entry.binding().kind())) {
                result.add(name);
            }
        });
        return result;
    }

    public void emit(final Consumer<BaseGenerator.GeneratedClass> writer, final String moduleName) throws IOException {
        if (emitted) {
            return;
        }
        if (missingState) {
            throw new IllegalStateException("Missing compact build state beside existing generated output; perform a full clean rebuild (including the class output)");
        }
        requiredOrigins.removeAll(touched);
        if (!requiredOrigins.isEmpty()) {
            throw new IllegalStateException("Missing required compact fragment cache for " + requiredOrigins +
                    "; perform a full clean rebuild (including the class output)");
        }
        entries.values().removeIf(this::deleted);
        for (final var entry : entries.values()) {
            if (!touched.contains(entry.origin()) && !entry.source().startsWith("file:")) {
                throw new IllegalStateException("Cannot verify source deletion for cached compact origin " + entry.origin() +
                        "; this compiler requires a full clean rebuild for partial recompilation");
            }
        }
        namespace = namespace(moduleName);
        final var holders = new ArrayList<BaseGenerator.GeneratedClass>();
        standalone(holders, moduleName);
        if (moduleName != null) {
            descriptors(holders, moduleName);
        }
        if (pendingUpdate != null) {
            Files.createDirectories(pendingUpdate.getParent());
            Files.writeString(pendingUpdate, "A successful final processing round is required before reusing this output.\n", UTF_8);
        }
        for (final var holder : holders) {
            writer.accept(new BaseGenerator.GeneratedClass(holder.name(), resolveReferences(holder.content())));
        }
        final var currentHolders = holders.stream().map(BaseGenerator.GeneratedClass::name).collect(java.util.stream.Collectors.toSet());
        for (final var previous : previousHolders) {
            if (!currentHolders.contains(previous)) {
                deleteGeneratedClass(environment, output, previous);
            }
        }
        persist();
        if (marker != null) {
            final var origins = new java.util.TreeSet<String>();
            entries.values().forEach(it -> origins.add(it.origin()));
            Files.createDirectories(marker.getParent());
            Files.write(marker, origins, UTF_8);
            Files.write(inventory, new java.util.TreeSet<>(currentHolders), UTF_8);
        }
        emitted = true;
    }

    /** Publication is not transactional; failed compilations must not reuse a partially updated inventory. */
    public void complete() throws IOException {
        if (emitted && pendingUpdate != null) {
            Files.deleteIfExists(pendingUpdate);
        }
    }

    /** Removes only a known processor-owned compilation unit, never business classes. */
    public static void deleteGeneratedClass(final ProcessingEnvironment environment, final Path output, final String name) throws IOException {
        final int separator = name.lastIndexOf('.');
        final var pack = separator < 0 ? "" : name.substring(0, separator);
        final var simple = name.substring(separator + 1);
        if (output != null) {
            Files.deleteIfExists(output.resolve(name.replace('.', '/') + ".class"));
        } else {
            deleteResource(environment, CLASS_OUTPUT, pack, simple + ".class");
        }
        deleteResource(environment, SOURCE_OUTPUT, pack, simple + ".java");
    }

    private static void deleteResource(final ProcessingEnvironment environment, final JavaFileManager.Location location,
                                       final String pack, final String name) throws IOException {
        try {
            final var resource = environment.getFiler().getResource(location, pack, name);
            if ("file".equals(resource.toUri().getScheme())) {
                Files.deleteIfExists(Path.of(resource.toUri()));
            } else {
                resource.delete();
            }
        } catch (final FileNotFoundException | NoSuchFileException missing) {
            // ECJ reports absent resources here instead of returning a nonexistent file object.
        }
    }

    private String resolveReferences(final String source) {
        return transform(source, Map.of(), false, true);
    }

    private String transform(final String source, final Map<String, String> replacements,
                             final boolean qualifyLookups, final boolean resolveFactories) {
        return code(source, body -> {
            if (!body.contains("$") && (!qualifyLookups || (!body.contains("lookup") && !body.contains("orderedList")))) {
                return body;
            }
            final var matcher = IDENTIFIER.matcher(body);
            final var result = new StringBuilder(body.length());
            int from = 0;
            while (matcher.find()) {
                final int start = matcher.start();
                int end = matcher.end();
                final var identifier = matcher.group();
                var replacement = replacements.containsKey("$method") && identifier.startsWith("$method_") ?
                        replacements.get("$method") + identifier.substring("$method".length()) : replacements.get(identifier);
                if (("$callback".equals(identifier) || "$helper".equals(identifier)) && end < body.length() && body.charAt(end) == '{') {
                    final int close = body.indexOf('}', end + 1);
                    if (close < 0) {
                        throw new IllegalArgumentException("Unclosed callback reference");
                    }
                    replacement = replacements.get(body.substring(start, close + 1));
                    if (replacement == null) {
                        throw new IllegalArgumentException("Unknown callback reference: " + body.substring(start, close + 1));
                    }
                    end = close + 1;
                    matcher.region(end, body.length());
                } else if ("$factory".equals(identifier) && end < body.length() && body.charAt(end) == '{') {
                    final int close = body.indexOf('}', end + 1);
                    if (close < 0) {
                        throw new IllegalArgumentException("Unclosed compact factory reference");
                    }
                    if (resolveFactories) {
                        final var id = body.substring(end + 1, close);
                        replacement = factories.get(id);
                        if (replacement == null) {
                            throw new IllegalArgumentException("Unknown compact factory reference: " + id);
                        }
                    } else {
                        replacement = body.substring(start, close + 1);
                    }
                    end = close + 1;
                    matcher.region(end, body.length());
                } else if (replacement == null && reserved(identifier)) {
                    throw new IllegalArgumentException("Unresolved reserved compact placeholder: " + identifier);
                } else if (qualifyLookups && ("lookup".equals(identifier) || "lookups".equals(identifier) || "orderedList".equals(identifier)) &&
                        body.startsWith("(container, ", end) && (start == 0 || body.charAt(start - 1) != '.')) {
                    replacement = "io.yupiik.fusion.framework.api.container.internal.DependencyLookups." + identifier;
                }
                result.append(body, from, start).append(replacement == null ? identifier : replacement);
                from = end;
            }
            return result.append(body, from, body.length()).toString();
        });
    }

    private boolean reserved(final String identifier) {
        return "$method".equals(identifier) || identifier.startsWith("$method_") ||
                "$factory".equals(identifier) || "$holder".equals(identifier);
    }

    // Templates are Java source, so placeholders must never be expanded in literals or comments.
    private String code(final String source, final UnaryOperator<String> transform) {
        final var result = new StringBuilder();
        int from = 0;
        int index = 0;
        while (index < source.length()) {
            final char current = source.charAt(index);
            final boolean line = source.startsWith("//", index);
            final boolean block = source.startsWith("/*", index);
            if (current != '"' && current != '\'' && !line && !block) {
                index++;
                continue;
            }
            result.append(transform.apply(source.substring(from, index)));
            final int start = index++;
            if (line) {
                while (index < source.length() && source.charAt(index) != '\n' && source.charAt(index) != '\r') {
                    index++;
                }
            } else if (block) {
                final int end = source.indexOf("*/", index + 1);
                index = end < 0 ? source.length() : end + 2;
            } else if (current == '"' && source.startsWith("\"\"\"", start)) {
                index = start + 3;
                while (index < source.length()) {
                    if (source.charAt(index) == '\\') {
                        index = Math.min(source.length(), index + 2);
                    } else if (source.startsWith("\"\"\"", index)) {
                        index += 3;
                        break;
                    } else {
                        index++;
                    }
                }
            } else {
                while (index < source.length()) {
                    final char next = source.charAt(index++);
                    if (next == '\\') {
                        index = Math.min(source.length(), index + 1);
                    } else if (next == current) {
                        break;
                    }
                }
            }
            result.append(source, start, index);
            from = index;
        }
        return result.append(transform.apply(source.substring(from))).toString();
    }

    private boolean deleted(final Entry entry) {
        if (touched.contains(entry.origin())) {
            return false;
        }
        if (!entry.source().isEmpty() && entry.source().startsWith("file:") && !Files.exists(Path.of(URI.create(entry.source())))) {
            return true;
        }
        return output != null && !Files.exists(output.resolve(entry.origin().replace('.', '/') + ".class"));
    }

    /** Manual methods live in the module unless package access or source size requires a real holder. */
    private void standalone(final List<BaseGenerator.GeneratedClass> holders, final String moduleName) {
        final var packages = new TreeMap<String, List<Map.Entry<String, Entry>>>();
        final var moduleEntries = new ArrayList<Map.Entry<String, Entry>>();
        final var modulePackage = moduleName == null ? null : packageName(moduleName);
        entries.entrySet().stream().filter(it -> it.getValue().binding().kind() == null).forEach(entry -> {
            final var binding = entry.getValue().binding();
            final int weight = constructionSize(entry.getKey(), new HashSet<>());
            if (binding.packageName().equals(modulePackage) && (long) moduleSize + weight <= SHARD_CHARS) {
                moduleEntries.add(entry);
                moduleSize += weight;
                factories.put(entry.getKey(), moduleName + "." + constructionMethod(entry) + "()");
            } else {
                packages.computeIfAbsent(binding.packageName(), ignored -> new ArrayList<>()).add(entry);
            }
        });
        final var batches = new TreeMap<String, List<Map.Entry<String, Entry>>>();
        for (final var pack : packages.entrySet()) {
            int shard = 0;
            int size = 0;
            var batch = new ArrayList<Map.Entry<String, Entry>>();
            for (final var entry : pack.getValue()) {
                final int weight = constructionSize(entry.getKey(), new HashSet<>());
                if (!batch.isEmpty() && (batch.size() == SHARD_SIZE || (long) size + weight > SHARD_CHARS)) {
                    batches.put(qualified(pack.getKey(), "FusionBindings_" + namespace + "_" + shard++), batch);
                    batch = new ArrayList<>();
                    size = 0;
                }
                batch.add(entry);
                size += weight;
            }
            if (!batch.isEmpty()) {
                batches.put(qualified(pack.getKey(), "FusionBindings_" + namespace + "_" + shard), batch);
            }
        }
        // Assign every construction owner before expanding any fragment.
        batches.forEach((name, batch) -> batch.forEach(entry ->
                factories.put(entry.getKey(), name + "." + constructionMethod(entry) + "()")));
        for (final var batch : batches.entrySet()) {
            final var name = batch.getKey();
            final var simple = name.substring(name.lastIndexOf('.') + 1);
            final var source = new StringBuilder();
            final var pack = packageName(name);
            if (!pack.isEmpty()) {
                source.append("package ").append(pack).append(";\n");
            }
            source.append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\npublic final class ").append(simple).append(" {\n");
            if (batch.getValue().stream().anyMatch(it -> context(it.getValue().binding()))) {
                contextConstructor(source, simple);
            } else {
                source.append("  private ").append(simple).append("() {}\n");
            }
            constructionMethods(source, batch.getValue(), name);
            holders.add(new BaseGenerator.GeneratedClass(name, source.append("}\n").toString()));
        }
        // Only actual size overflow permits a stable module method to forward to a holder.
        if (moduleName != null) {
            for (final var batch : batches.values()) {
                for (final var entry : batch) {
                    if (entry.getValue().binding().packageName().equals(modulePackage)) {
                        moduleHelpers.append("  public static ").append(entry.getValue().binding().registrationType()).append(' ')
                                .append(constructionMethod(entry)).append("() { return ").append(factories.get(entry.getKey())).append("; }\n");
                    }
                }
            }
        }
        if (!moduleEntries.isEmpty()) {
            if (moduleEntries.stream().anyMatch(it -> context(it.getValue().binding()))) {
                final var simple = moduleName.substring(moduleName.lastIndexOf('.') + 1);
                contextConstructor(moduleHelpers, simple);
                moduleHelpers.append("  public ").append(simple).append("() { this(null, null, null); }\n");
            }
            constructionMethods(moduleHelpers, moduleEntries, moduleName);
        }
    }

    private void contextConstructor(final StringBuilder source, final String simple) {
        source.append("  private final io.yupiik.fusion.framework.api.configuration.Configuration configuration;\n")
                .append("  private final java.util.function.Function<String, String> keyMapper;\n")
                .append("  private final String prefix;\n")
                .append("  private ").append(simple)
                .append("(final io.yupiik.fusion.framework.api.configuration.Configuration configuration, ")
                .append("final java.util.function.Function<String, String> keyMapper, final String prefix) {\n")
                .append("    this.configuration = configuration; this.keyMapper = keyMapper; this.prefix = prefix;\n  }\n");
    }

    private boolean context(final Binding binding) {
        return binding.methods().stream().anyMatch(it -> requiresContext(it.declaration()));
    }

    private String qualified(final String pack, final String simple) {
        return pack.isEmpty() ? simple : pack + '.' + simple;
    }

    private String packageName(final String name) {
        final int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(0, separator);
    }

    private String constructionMethod(final Map.Entry<String, Entry> entry) {
        final var pack = entry.getValue().binding().packageName();
        return entry.getKey().substring(pack.isEmpty() ? 0 : pack.length() + 1).replace('.', '$');
    }

    private void constructionMethods(final StringBuilder source, final List<Map.Entry<String, Entry>> artifacts, final String owner) {
        for (int index = 0; index < artifacts.size(); index++) {
            final var entry = artifacts.get(index);
            final var fragment = fragment(entry.getKey(), entry.getValue().binding(), owner, "call" + index + "_");
            source.append("  public static ").append(entry.getValue().binding().registrationType()).append(' ')
                    .append(constructionMethod(entry)).append("() { return ").append(fragment.expression()).append("; }\n")
                    .append(fragment.helpers());
        }
    }

    /** Conservative transitive weight, including repeated source references and callback metadata. */
    private int constructionSize(final String id, final Set<String> visiting) {
        final var entry = entries.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown compact factory reference: " + id);
        }
        final var binding = entry.binding();
        if (!visiting.add(id)) {
            return binding.sourceSize();
        }
        long size = binding.sourceSize();
        final var sources = new ArrayList<String>();
        sources.add(binding.factoryExpression());
        binding.methods().forEach(it -> {
            sources.add(it.declaration());
            sources.add(it.inlineExpression());
        });
        for (final var source : sources) {
            final var weight = new long[1];
            code(source, body -> {
                final var matcher = FACTORY_REFERENCE.matcher(body);
                while (matcher.find()) {
                    weight[0] += constructionSize(matcher.group(1), visiting);
                }
                return body;
            });
            size += weight[0];
        }
        visiting.remove(id);
        return (int) Math.min(Integer.MAX_VALUE, size);
    }

    private void descriptors(final List<BaseGenerator.GeneratedClass> holders, final String moduleName) {
        final int separator = moduleName.lastIndexOf('.');
        final var modulePackage = separator < 0 ? "" : moduleName.substring(0, separator);
        int ordinal = 0;
        int shard = 0;
        for (final var kind : List.of("bean", "listener")) {
            final var artifacts = entries.entrySet().stream().filter(it -> kind.equals(it.getValue().binding().kind())).toList();
            int index = 0;
            while (index < artifacts.size()) {
                final var artifact = artifacts.get(index);
                final var binding = artifact.getValue().binding();
                final var pack = binding.packageName();
                final int weight = constructionSize(artifact.getKey(), new HashSet<>());
                if (pack.equals(modulePackage) && (long) moduleSize + weight <= SHARD_CHARS) {
                    final var fragment = fragment(artifact.getKey(), binding, moduleName, "binding" + ordinal++ + "_");
                    descriptorRegistrations.put(artifact.getKey(), "builder.add(" + resolveReferences(fragment.expression()) + ");");
                    moduleHelpers.append(fragment.helpers());
                    moduleSize += weight;
                    index++;
                    continue;
                }
                if (pack.isEmpty() && !modulePackage.isEmpty()) {
                    throw new IllegalArgumentException("A named-package Fusion module cannot register default-package bindings: " + artifact.getKey());
                }
                final var batch = new ArrayList<Map.Entry<String, Entry>>();
                int size = 0;
                while (index < artifacts.size()) {
                    final var next = artifacts.get(index);
                    final var nextBinding = next.getValue().binding();
                    if (!pack.equals(nextBinding.packageName()) ||
                            (!batch.isEmpty() && (batch.size() == SHARD_SIZE || (long) size + constructionSize(next.getKey(), new HashSet<>()) > SHARD_CHARS))) {
                        break;
                    }
                    batch.add(next);
                    size += constructionSize(next.getKey(), new HashSet<>());
                    index++;
                }
                final var simple = "FusionBindings_" + namespace + "_" + kind + "_" + shard++;
                final var name = (pack.isEmpty() ? "" : pack + '.') + simple;
                final var method = kind.equals("bean") ? "beans" : "listeners";
                final var type = kind.equals("bean") ? "io.yupiik.fusion.framework.api.container.FusionBean<?>" :
                        "io.yupiik.fusion.framework.api.container.FusionListener<?>";
                final var source = new StringBuilder();
                if (!pack.isEmpty()) {
                    source.append("package ").append(pack).append(";\n");
                }
                source.append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\npublic final class ").append(simple)
                        .append(" {\n")
                        .append("  private ").append(simple).append("() {}\n")
                        .append("  public static java.util.stream.Stream<").append(type).append("> ").append(method).append("() {\n")
                        .append("    final var builder = java.util.stream.Stream.<").append(type).append(">builder();\n");
                final var helpers = new StringBuilder();
                for (int id = 0; id < batch.size(); id++) {
                    final var entry = batch.get(id);
                    final var fragment = fragment(entry.getKey(), entry.getValue().binding(), name, "binding" + id + "_");
                    source.append("    builder.add(").append(fragment.expression()).append(");\n");
                    helpers.append(fragment.helpers());
                    descriptorRegistrations.put(entry.getKey(), "");
                }
                source.append("    return builder.build();\n  }\n").append(helpers).append("}\n");
                holders.add(new BaseGenerator.GeneratedClass(name, source.toString()));
                descriptorRegistrations.put(batch.get(0).getKey(), name + "." + method + "().forEachOrdered(builder::add);");
            }
        }
    }

    private Fragment fragment(final String stableId, final Binding binding, final String owner,
                              final String prefix) {
        final var expressions = ownerExpressions.computeIfAbsent(owner, ignored -> new TreeMap<>());
        final var existing = expressions.get(stableId);
        if (existing != null) {
            return new Fragment(existing, "");
        }
        if (!expanding.add(owner + ':' + stableId)) {
            throw new IllegalArgumentException("Cyclic compact construction reference: " + stableId);
        }
        final var symbols = new TreeMap<String, String>();
        symbols.put("$holder", owner);
        for (final var method : binding.methods()) {
            symbols.put(Binding.helperReference(method.operation()), owner + "::" + prefix + method.operation());
            symbols.put(Binding.helperCall(method.operation()), owner + "." + prefix + method.operation());
        }
        final var inlined = new HashSet<Integer>();
        // Inline lambdas belong only in construction expressions, never in retained helper declarations.
        final var factorySymbols = new TreeMap<>(symbols);
        var expression = transform(binding.factoryExpression(), factorySymbols, true, false);
        if (expression.length() <= FACTORY_CHARS) {
            for (final var method : binding.methods()) {
                if (!method.inlineExpression().isEmpty()) {
                    final var token = Binding.helperReference(method.operation());
                    final var previous = factorySymbols.put(token, "(" + transform(method.inlineExpression(), symbols, true, false) + ")");
                    final var candidate = transform(binding.factoryExpression(), factorySymbols, true, false);
                    if (candidate.length() <= FACTORY_CHARS) {
                        expression = candidate;
                        inlined.add(method.operation());
                    } else {
                        factorySymbols.put(token, previous);
                    }
                }
            }
        }
        final var helpers = new StringBuilder();
        final var retained = retainedHelpers(binding, inlined);
        final var operations = new HashSet<Integer>();
        for (final var method : binding.methods()) {
            if (!operations.add(method.operation())) {
                throw new IllegalArgumentException("Duplicate helper identity in " + stableId + ": " + method.operation());
            }
            if (retained.contains(method.operation())) {
                symbols.put("$method", prefix + method.operation());
                final var declaration = method.declaration().stripLeading();
                helpers.append("  ").append(transform(declaration, symbols, true, false)).append('\n');
            }
        }
        final var nestedHelpers = new StringBuilder();
        expression = expandConstruction(expression, owner, nestedHelpers);
        final var declarations = expandConstruction(helpers.toString(), owner, nestedHelpers);
        expressions.put(stableId, expression);
        expanding.remove(owner + ':' + stableId);
        return new Fragment(expression, nestedHelpers.append(declarations).toString());
    }

    private String expandConstruction(final String source, final String owner, final StringBuilder helpers) {
        return code(source, body -> {
            final var matcher = FACTORY_REFERENCE.matcher(body);
            final var result = new StringBuilder();
            while (matcher.find()) {
                final var id = matcher.group(1);
                final var entry = entries.get(id);
                if (entry == null) {
                    throw new IllegalArgumentException("Unknown compact factory reference: " + id);
                }
                final var binding = entry.binding();
                final boolean sameOwner = factories.getOrDefault(id, "").startsWith(owner + '.');
                final String expression;
                if (binding.packageName().equals(packageName(owner)) && sameOwner && !expanding.contains(owner + ':' + id)) {
                    final var nested = fragment(id, binding, owner, "construction" + nestedOrdinal++ + "_");
                    helpers.append(nested.helpers());
                    // Generators explicitly type functional expressions so chained calls have a target type.
                    // Preserve that cast instead of adding a redundant second cast around it.
                    expression = binding.factoryExpression().startsWith("(" + binding.registrationType() + ") ") ?
                            "(" + nested.expression() + ")" :
                            "((" + binding.registrationType() + ") (" + nested.expression() + "))";
                } else {
                    expression = factories.get(id);
                    if (expression == null) {
                        throw new IllegalArgumentException("No accessible construction owner for " + id);
                    }
                }
                matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(expression));
            }
            return matcher.appendTail(result).toString();
        });
    }

    private record Fragment(String expression, String helpers) {
    }

    private Set<Integer> retainedHelpers(final Binding binding, final Set<Integer> inlined) {
        final var retained = new HashSet<Integer>();
        for (final var method : binding.methods()) {
            if (!inlined.contains(method.operation()) || containsCode(binding.factoryExpression(), Binding.helperCall(method.operation()))) {
                retained.add(method.operation());
            }
        }
        // Inline metadata is explicit generated source, not an inferred rewrite of business Java.
        for (final var method : binding.methods()) {
            if (inlined.contains(method.operation())) {
                retainReferences(method.inlineExpression(), retained);
            }
        }
        int previous;
        do {
            previous = retained.size();
            for (final var method : binding.methods()) {
                if (retained.contains(method.operation())) {
                    retainReferences(method.declaration(), retained);
                }
            }
        } while (previous != retained.size());
        return retained;
    }

    private void retainReferences(final String source, final Set<Integer> retained) {
        code(source, body -> {
            final var references = HELPER_REFERENCE.matcher(body);
            while (references.find()) {
                retained.add(Integer.parseInt(references.group(1)));
            }
            return body;
        });
    }

    private boolean containsCode(final String source, final String token) {
        final var found = new boolean[1];
        code(source, body -> {
            found[0] |= body.contains(token);
            return body;
        });
        return found[0];
    }

    private boolean requiresContext(final String declaration) {
        final var required = new boolean[1];
        code(declaration, body -> {
            if (IDENTIFIER.matcher(body).results().anyMatch(it -> "$holder".equals(it.group()))) {
                required[0] = true;
            }
            return body;
        });
        return required[0];
    }

    private String namespace(final String moduleName) throws IOException {
        // Canonical, versioned logical inventory: provenance paths never affect emitted identities.
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            try (final var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                text(out, "fusion-bindings-namespace");
                out.writeInt(1);
                text(out, moduleName == null ? "" : moduleName);
                text(out, environment instanceof ModuleProcessingEnvironment scoped ? scoped.moduleName() : "");
                final var options = List.of("fusion.generateModule", "fusion.generateBeanForCliCommands",
                        "fusion.generateBeanForHttpEndpoints", "fusion.generateBeanForJsonRpcEndpoints",
                        "fusion.generateBeanForPersistenceEntities", "fusion.generateBeanForJsonCodec",
                        "fusion.generateBeanForRootConfiguration");
                out.writeInt(options.size());
                for (final var option : options) {
                    text(out, option);
                    out.writeBoolean(Boolean.parseBoolean(environment.getOptions().getOrDefault(option, "true")));
                }
                out.writeInt(entries.size());
                for (final var entry : entries.entrySet()) {
                    final var binding = entry.getValue().binding();
                    text(out, entry.getKey());
                    text(out, entry.getValue().origin());
                    text(out, binding.packageName());
                    text(out, binding.kind() == null ? "" : binding.kind());
                    text(out, binding.registrationType());
                    text(out, binding.factoryExpression());
                    out.writeInt(binding.methods().size());
                    for (final var method : binding.methods()) {
                        out.writeInt(method.operation());
                        text(out, method.declaration());
                        text(out, method.inlineExpression());
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest(), 0, 12);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void load() throws IOException {
        try (final var in = new DataInputStream(Files.newInputStream(cache))) {
            final int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported fragment cache version");
            }
            final int count = in.readInt();
            if (count < 0 || count > 1_000_000) {
                throw new IOException("Invalid fragment cache entry count");
            }
            for (int i = 0; i < count; i++) {
                final var name = text(in);
                final var origin = text(in);
                final var source = text(in);
                final var pack = text(in);
                final var kind = text(in);
                final var type = text(in);
                final var factory = text(in);
                final int methodCount = in.readInt();
                if (methodCount < 0 || methodCount > 10_000) {
                    throw new IOException("Invalid fragment cache method count");
                }
                final var methods = new ArrayList<Binding.Method>();
                for (int j = 0; j < methodCount; j++) {
                    methods.add(new Binding.Method(in.readInt(), text(in), text(in)));
                }
                entries.put(name, new Entry(origin, source, new Binding(pack, kind.isEmpty() ? null : kind, type, factory, methods)));
                origins.computeIfAbsent(origin, ignored -> new HashSet<>()).add(name);
            }
        }
    }

    private void persist() throws IOException {
        if (cache == null) {
            return;
        }
        Files.createDirectories(cache.getParent());
        final var temporary = Files.createTempFile(cache.getParent(), "compact-", ".tmp");
        try {
            try (final var out = new DataOutputStream(Files.newOutputStream(temporary))) {
                out.writeInt(VERSION);
                out.writeInt(entries.size());
                for (final var entry : entries.entrySet()) {
                    final var binding = entry.getValue().binding();
                    text(out, entry.getKey());
                    text(out, entry.getValue().origin());
                    text(out, entry.getValue().source());
                    text(out, binding.packageName());
                    text(out, binding.kind() == null ? "" : binding.kind());
                    text(out, binding.registrationType());
                    text(out, binding.factoryExpression());
                    out.writeInt(binding.methods().size());
                    for (final var method : binding.methods()) {
                        out.writeInt(method.operation());
                        text(out, method.declaration());
                        text(out, method.inlineExpression());
                    }
                }
            }
            Files.move(temporary, cache, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private String text(final DataInputStream in) throws IOException {
        final int size = in.readInt();
        if (size < 0 || size > 16_000_000) {
            throw new IOException("Invalid fragment cache string size");
        }
        final var bytes = in.readNBytes(size);
        if (bytes.length != size) {
            throw new IOException("Truncated fragment cache");
        }
        return new String(bytes, UTF_8);
    }

    private void text(final DataOutputStream out, final String value) throws IOException {
        final var bytes = value.getBytes(UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private record Entry(String origin, String source, Binding binding) {
    }

}
