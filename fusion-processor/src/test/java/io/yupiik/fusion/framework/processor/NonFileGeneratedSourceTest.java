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
package io.yupiik.fusion.framework.processor;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.ForwardingJavaFileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonFileGeneratedSourceTest {
    @TempDir
    Path work;

    @Test
    void compilesDirectFactoriesAndDeletesOnlyOwnedCoordinationSources() throws Exception {
        verifyCompilation(true, true);
    }

    @Test
    void refusingProviderRetainsCoordinationSourcesWithoutRuntimeArtifacts() throws Exception {
        verifyCompilation(false, true);
    }

    @Test
    void unwrappedJavacDoesNotForwardDeletionToTheSupportingManager() throws Exception {
        // Standard JSR 269 does not guarantee deletion support. Javac's Filer output wrapper
        // returns false without reaching the manager, even when the manager supports deletion.
        verifyCompilation(true, false);
    }

    @SuppressWarnings("unchecked")
    private void verifyCompilation(final boolean allowDeletion, final boolean forwardDeletion) throws Exception {
        final var sourceRoot = Files.createDirectories(work.resolve("src/example"));
        final var model = sourceRoot.resolve("Settings.java");
        Files.writeString(model, """
                package example;
                @io.yupiik.fusion.framework.build.api.configuration.RootConfiguration("app")
                public record Settings(String name, int count) {}
                """);
        final var packageInfo = sourceRoot.resolve("package-info.java");
        final var packageContent = "@Deprecated\npackage example;\n";
        Files.writeString(packageInfo, packageContent);
        final var classes = Files.createDirectories(work.resolve("classes"));
        final var generated = Files.createDirectories(work.resolve("generated"));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new StringWriter();
        try (final var standard = compiler.getStandardFileManager(null, null, UTF_8);
             final var manager = new MemorySourceManager(standard, allowDeletion)) {
            standard.setLocationFromPaths(CLASS_OUTPUT, List.of(classes));
            standard.setLocationFromPaths(SOURCE_OUTPUT, List.of(generated));
            final var task = compiler.getTask(diagnostics, manager, null, List.of(
                    "--release", "17", "-Werror", "-classpath", System.getProperty("java.class.path"),
                    "-Afusion.workdir=" + work.resolve("state"), "-Afusion.generateModule=false",
                    "-Afusion.generateBeanForRootConfiguration=false"), null,
                    standard.getJavaFileObjectsFromPaths(List.of(model, packageInfo)));
            task.setProcessors(List.of(forwardDeletion ? new FusionProcessor() {
                @Override
                public void init(final ProcessingEnvironment environment) {
                    super.init(new DeletingEnvironment(environment, manager));
                }
            } : new FusionProcessor()));
            assertTrue(task.call(), diagnostics.toString());

            final var coordination = manager.outputs.stream().filter(MemorySource::isCoordination).toList();
            assertFalse(coordination.isEmpty(), "Real processing rounds must create coordination sources");
            assertTrue(manager.outputs.stream().anyMatch(it -> !it.isCoordination()),
                    "The direct factory owner must also have been compiled from a memory source");
            try (final var files = Files.walk(classes)) {
                assertFalse(files.anyMatch(it -> it.toString().contains("compact_")),
                        "Coordination sources must not create runtime classes or resources");
            }
            try (final var files = Files.walk(generated)) {
                assertEquals(0, files.filter(Files::isRegularFile).count(), "SOURCE_OUTPUT must stay in memory");
            }
            assertEquals(packageContent, Files.readString(packageInfo), "Application package-info must be untouched");
            assertTrue(Files.isRegularFile(classes.resolve("example/package-info.class")));
            try (final var loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
                final BiFunction<Configuration, Function<String, String>, Object> factory = GeneratedFactories.invoke(
                        loader, "example", "Settings$FusionConfigurationFactory", BiFunction.class);
                final Configuration configuration = key -> Optional.ofNullable(Map.of(
                        "app.name", "memory-source", "app.count", "42").get(key));
                final var settings = factory.apply(configuration, Function.identity());
                assertEquals("memory-source", settings.getClass().getMethod("name").invoke(settings));
                assertEquals(42, settings.getClass().getMethod("count").invoke(settings));
            }
            for (final var source : manager.outputs) {
                assertEquals("mem", source.toUri().getScheme());
                final var expectedAttempts = source.isCoordination() && forwardDeletion ? 1 : 0;
                final var expectedDeletion = expectedAttempts == 1 && allowDeletion;
                assertEquals(expectedAttempts, source.deletionAttempts, source.toUri().toString());
                assertEquals(expectedDeletion, source.deleted, source.toUri().toString());
                assertEquals(!source.deleted, manager.live.containsKey(source.name), source.toUri().toString());
            }
        }
    }

    // Only public standard interfaces are wrapped. Writes still pass through javac's Filer
    // so its round tracking is preserved; only delete reaches the actual provider directly.
    private record DeletingEnvironment(ProcessingEnvironment delegate, MemorySourceManager manager)
            implements ProcessingEnvironment {
        @Override
        public Filer getFiler() {
            final var filer = delegate.getFiler();
            return new Filer() {
                @Override
                public JavaFileObject createSourceFile(final CharSequence name, final Element... origins) throws IOException {
                    final var original = filer.createSourceFile(name, origins);
                    final var source = manager.outputs.stream()
                            .filter(it -> it.toUri().equals(original.toUri()))
                            .findFirst().orElseThrow();
                    return new ForwardingJavaFileObject<JavaFileObject>(original) {
                        @Override
                        public boolean delete() {
                            return source.delete();
                        }
                    };
                }

                @Override
                public JavaFileObject createClassFile(final CharSequence name, final Element... origins) throws IOException {
                    return filer.createClassFile(name, origins);
                }

                @Override
                public FileObject createResource(final JavaFileManager.Location location, final CharSequence pkg,
                                                 final CharSequence name, final Element... origins) throws IOException {
                    return filer.createResource(location, pkg, name, origins);
                }

                @Override
                public FileObject getResource(final JavaFileManager.Location location, final CharSequence pkg,
                                              final CharSequence name) throws IOException {
                    // Preserve the real compiler's resource/source fallback behavior.
                    return filer.getResource(location, pkg, name);
                }
            };
        }

        @Override
        public Map<String, String> getOptions() {
            return delegate.getOptions();
        }

        @Override
        public Messager getMessager() {
            return delegate.getMessager();
        }

        @Override
        public Elements getElementUtils() {
            return delegate.getElementUtils();
        }

        @Override
        public Types getTypeUtils() {
            return delegate.getTypeUtils();
        }

        @Override
        public SourceVersion getSourceVersion() {
            return delegate.getSourceVersion();
        }

        @Override
        public Locale getLocale() {
            return delegate.getLocale();
        }

        @Override
        public boolean isPreviewEnabled() {
            return delegate.isPreviewEnabled();
        }
    }

    private static final class MemorySourceManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final boolean allowDeletion;
        private final List<MemorySource> outputs = new ArrayList<>();
        private final Map<String, MemorySource> live = new LinkedHashMap<>();

        private MemorySourceManager(final StandardJavaFileManager delegate, final boolean allowDeletion) {
            super(delegate);
            this.allowDeletion = allowDeletion;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(final Location location, final String className,
                                                  final JavaFileObject.Kind kind, final FileObject sibling) throws IOException {
            if (location == SOURCE_OUTPUT && kind == JavaFileObject.Kind.SOURCE) {
                final var name = className.replace('.', '/') + kind.extension;
                final var source = new MemorySource(name, allowDeletion, live);
                outputs.add(source);
                live.put(name, source);
                return source;
            }
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }

        @Override
        public FileObject getFileForInput(final JavaFileManager.Location location, final String packageName,
                                          final String relativeName) throws IOException {
            if (location == SOURCE_OUTPUT) {
                final var source = live.get((packageName.isEmpty() ? "" : packageName.replace('.', '/') + '/') + relativeName);
                if (source != null) {
                    return source;
                }
            }
            return super.getFileForInput(location, packageName, relativeName);
        }

        @Override
        public String inferBinaryName(final Location location, final JavaFileObject file) {
            if (file instanceof MemorySource source) {
                return source.name.substring(0, source.name.length() - JavaFileObject.Kind.SOURCE.extension.length()).replace('/', '.');
            }
            return super.inferBinaryName(location, file);
        }

        @Override
        public boolean isSameFile(final FileObject first, final FileObject second) {
            if (first instanceof MemorySource || second instanceof MemorySource) {
                return first.toUri().equals(second.toUri());
            }
            return super.isSameFile(first, second);
        }
    }

    private static final class MemorySource extends SimpleJavaFileObject {
        private final String name;
        private final boolean allowDeletion;
        private final Map<String, MemorySource> live;
        private final StringWriter content = new StringWriter();
        private int deletionAttempts;
        private boolean deleted;

        private MemorySource(final String name, final boolean allowDeletion, final Map<String, MemorySource> live) {
            super(URI.create("mem:///" + name), Kind.SOURCE);
            this.name = name;
            this.allowDeletion = allowDeletion;
            this.live = live;
        }

        private boolean isCoordination() {
            return name.startsWith("io/yupiik/fusion/generated/compact_");
        }

        @Override
        public Writer openWriter() {
            return content;
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return content.toString();
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(content.toString().getBytes(UTF_8));
        }

        @Override
        public boolean delete() {
            deletionAttempts++;
            if (!allowDeletion) {
                return false;
            }
            deleted = true;
            live.remove(name);
            return true;
        }
    }
}
