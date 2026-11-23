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

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor;
import io.yupiik.fusion.framework.processor.test.ProcessorInternals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.Processor;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.JarFile;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataDependencyProcessorTest {
    private static final String DESCRIPTORS = "META-INF/fusion/metadata/aliases-v1";

    @TempDir
    Path work;

    @Test
    void loadsAliasesFromEveryDependencyJarWithSeparateProcessorPath() throws Exception {
        final var first = dependency("first", "First", "one");
        final var second = dependency("second", "Second", "two");
        final var output = work.resolve("consumer");
        compile(output, Map.of("app.Service", """
                package app;
                @dep.First @dep.Second
                @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(first, second), true, true);
        assertEquals(Map.of("First", "one", "Second", "two"), metadata(output, List.of(first, second)));
        assertNoDescriptors(output);
    }

    @Test
    void duplicateAnnotationsUseActualClasspathOrder() throws Exception {
        final var earlier = dependency("a-aliases", "First", "earlier");
        final var later = dependency("z-aliases", "First", "later");
        final var output = work.resolve("consumer");
        compile(output, Map.of("app.Service", """
                package app;
                @dep.First @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(later, earlier), true);
        assertEquals(Map.of("First", "later"), metadata(output, List.of(later, earlier)));
        final var reversed = work.resolve("reversed");
        compile(reversed, Map.of("app.Service", """
                package app;
                @dep.First @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(earlier, later), true);
        assertEquals(Map.of("First", "earlier"), metadata(reversed, List.of(earlier, later)));
    }

    @Test
    void handwrittenProvidersRemainSupportedAndOverrideDependencyAliases() throws Exception {
        final var dependency = dependency("aliases", "First", "descriptor");
        final var providers = work.resolve("providers");
        compile(providers, Map.of("manual.Provider", """
                package manual;
                public class Provider implements io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor {
                    public String annotationType() { return "dep.First"; }
                    public String name() { return "First"; }
                    public String value() { return "handwritten"; }
                }
                """), List.of(dependency), false);
        final var index = providers.resolve("META-INF/services/" + MetadataContributor.class.getName());
        Files.createDirectories(index.getParent());
        Files.writeString(index, "manual.Provider\n");
        final var output = work.resolve("consumer");
        compile(output, Map.of("app.Service", """
                package app;
                @dep.First @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(dependency, providers), true);
        assertEquals(Map.of("First", "handwritten"), metadata(output, List.of(dependency, providers)));
        final var local = work.resolve("local");
        compile(local, Map.of("dep.First", """
                package dep;
                @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="First", value="current")
                public @interface First {}
                """, "app.Service", """
                package app;
                @dep.First @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(dependency, providers), true);
        assertEquals(Map.of("First", "current"), metadata(local, List.of(dependency, providers)));
        compile(local, Map.of("dep.First", "package dep; public @interface First {}",
                "app.Service", """
                        package app;
                        @dep.First @io.yupiik.fusion.framework.build.api.scanning.Bean
                        public class Service {}
                        """), List.of(dependency, providers, local), true);
        // Removing an alias does not remove an explicitly supplied handwritten provider.
        assertEquals(Map.of("First", "handwritten"), metadata(local, List.of(dependency, providers)));
    }

    @Test
    void removedLocalAliasIsNotReloadedFromOutputOnTheClasspath() throws Exception {
        final var output = work.resolve("consumer");
        compile(output, Map.of(
                "app.Flag", """
                        package app;
                        @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="flag", value="old")
                        public @interface Flag {}
                        """,
                "app.Service", """
                        package app;
                        @Flag @io.yupiik.fusion.framework.build.api.scanning.Bean
                        public class Service {}
                        """), List.of(), true);
        assertEquals(Map.of("flag", "old"), metadata(output, List.of()));
        compile(output, Map.of(
                "app.Flag", "package app; public @interface Flag {}",
                "app.Service", """
                        package app;
                        @Flag @io.yupiik.fusion.framework.build.api.scanning.Bean
                        public class Service {}
                        """), List.of(output), true);
        assertEquals(Map.of(), metadata(output, List.of()));
        assertNoDescriptors(output);
    }

    private Path dependency(final String directory, final String annotation, final String value) throws Exception {
        final var output = work.resolve(directory);
        compile(output, Map.of("dep." + annotation, """
                package dep;
                @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="%s", value="%s")
                public @interface %s {}
                """.formatted(annotation, value, annotation)), List.of(), true);
        return jar(output, directory);
    }

    private Path jar(final Path output, final String directory) throws Exception {
        final var jar = work.resolve(directory + ".jar");
        try (final var archive = new JarOutputStream(Files.newOutputStream(jar));
             final var files = Files.walk(output)) {
            for (final var file : files.filter(Files::isRegularFile).toList()) {
                archive.putNextEntry(new JarEntry(output.relativize(file).toString().replace(File.separatorChar, '/')));
                Files.copy(file, archive);
                archive.closeEntry();
            }
        }
        try (final var archive = new JarFile(jar.toFile())) {
            assertTrue(archive.stream().noneMatch(it -> it.getName().equals(DESCRIPTORS) ||
                    it.getName().contains("metadata-aliases") || it.getName().contains("FusionMetadataContributor") ||
                    it.getName().equals("META-INF/services/" + MetadataContributor.class.getName())));
        }
        return jar;
    }

    @Test
    void nestedDependencyAliasesRetainDefaultsAndUsageOverrides() throws Exception {
        final var annotations = work.resolve("nested");
        compile(annotations, Map.of("dep.Aliases", """
                package dep;
                public class Aliases {
                    @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="constant", value="true")
                    public @interface Constant {}
                    @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="dynamic")
                    public @interface Dynamic { int value() default 7; }
                }
                """), List.of(), true);
        final var dependency = jar(annotations, "nested");
        final var output = work.resolve("consumer");
        compile(output, Map.of("app.Service", """
                package app;
                @dep.Aliases.Constant @dep.Aliases.Dynamic(42)
                @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(dependency), true);
        assertEquals(Map.of("constant", "true", "dynamic", "42"), metadata(output, List.of(dependency)));
    }

    @Test
    void classLiteralsAndNameOverridesNeedNoBuildAnnotationsAtRuntime() throws Exception {
        final var annotations = work.resolve("typed");
        compile(annotations, Map.of("dep.Typed", """
                package dep;
                @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="fallback", value="fallback")
                public @interface Typed {
                    String name() default "declared";
                    Class<?> value() default String.class;
                }
                """), List.of(), true);
        final var dependency = jar(annotations, "typed");
        final var output = work.resolve("consumer");
        compile(output, Map.of("app.Service", """
                package app;
                @dep.Typed(name="renamed", value=Integer.class)
                @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(dependency), true);
        assertEquals(Map.of("\"renamed\"", "java.lang.Integer.class"), metadata(output, List.of(dependency)));
        final var defaults = work.resolve("defaults");
        compile(defaults, Map.of("app.Service", """
                package app;
                @dep.Typed @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(dependency), true);
        assertEquals(Map.of("\"declared\"", "java.lang.String.class"), metadata(defaults, List.of(dependency)));
        // The isolated runtime has only the runtime API, the application, and its annotation JAR.
        try (final var runtime = new URLClassLoader(new URL[]{output.toUri().toURL(), dependency.toUri().toURL(),
                FusionBean.class.getProtectionDomain().getCodeSource().getLocation()}, ClassLoader.getPlatformClassLoader())) {
            assertThrows(ClassNotFoundException.class, () -> runtime.loadClass(
                    "io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias"));
            assertEquals(0, runtime.loadClass("dep.Typed").getDeclaredAnnotations().length);
            final var beanType = runtime.loadClass(FusionBean.class.getName());
            final var bean = GeneratedFactories.registrations(runtime, "beans").filter(it -> {
                try {
                    return beanType.getMethod("type").invoke(it).equals(runtime.loadClass("app.Service"));
                } catch (final ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }).findFirst().orElseThrow();
            assertEquals(Map.of("\"renamed\"", "java.lang.Integer.class"), beanType.getMethod("data").invoke(bean));
        }
    }

    @Test
    void handwrittenProvidersUseClassNameOrderNotServiceFileOrder() throws Exception {
        final var dependency = dependency("aliases", "First", "alias");
        final var providers = work.resolve("providers");
        final var sources = new HashMap<String, String>();
        for (final var name : List.of("AProvider", "ZProvider")) {
            sources.put("manual." + name, """
                    package manual;
                    public class %s implements io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor {
                        public String annotationType() { return "dep.First"; }
                        public String name() { return "First"; }
                        public String value() { return "%s"; }
                    }
                    """.formatted(name, name));
        }
        compile(providers, sources, List.of(dependency), false);
        final var index = providers.resolve("META-INF/services/" + MetadataContributor.class.getName());
        Files.createDirectories(index.getParent());
        Files.writeString(index, "manual.MissingProvider\nmanual.ZProvider\nmanual.AProvider\n");
        final var output = work.resolve("consumer");
        compile(output, Map.of("app.Service", """
                package app;
                @dep.First @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(dependency, providers), true);
        assertEquals(Map.of("First", "ZProvider"), metadata(output, List.of(dependency, providers)));
    }

    @Test
    void currentSourceOverridesDependencyAndRemovalShadowsDependency() throws Exception {
        final var dependency = dependency("dependency", "First", "dependency");
        final var output = work.resolve("consumer");
        final var service = """
                package app;
                @dep.First @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """;
        compile(output, Map.of("app.Service", service, "dep.First", """
                package dep;
                @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="First", value="source")
                public @interface First {}
                """), List.of(dependency), true);
        assertEquals(Map.of("First", "source"), metadata(output, List.of(dependency)));
        compile(output, Map.of("app.Service", service, "dep.First", "package dep; public @interface First {}"),
                List.of(dependency, output), true);
        assertEquals(Map.of(), metadata(output, List.of(dependency)));
        assertNoDescriptors(output);
    }

    @Test
    void unchangedLocalAliasIsDiscoveredFromIncrementalClassOutput() throws Exception {
        final var output = work.resolve("consumer");
        compile(output, Map.of("app.Flag", """
                package app;
                @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="flag", value="retained")
                public @interface Flag {}
                """), List.of(), true);
        compile(output, Map.of("app.Service", """
                package app;
                @Flag @io.yupiik.fusion.framework.build.api.scanning.Bean
                public class Service {}
                """), List.of(output), true);
        assertEquals(Map.of("flag", "retained"), metadata(output, List.of()));
        assertNoDescriptors(output);
    }

    private Map<String, Object> metadata(final Path output, final List<Path> dependencies) throws Exception {
        final var paths = new ArrayList<Path>();
        paths.add(output);
        paths.addAll(dependencies);
        try (final var loader = loader(paths)) {
            final FusionBean<?> bean = GeneratedFactories.bean(loader, loader.loadClass("app.Service"));
            return bean.data();
        }
    }

    private void compile(final Path output, final Map<String, String> sources,
                         final List<Path> dependencies, final boolean processing) throws Exception {
        compile(output, sources, dependencies, processing, false);
    }

    private void compile(final Path output, final Map<String, String> sources,
                         final List<Path> dependencies, final boolean processing, final boolean separateProcessorPath) throws Exception {
        Files.createDirectories(output);
        final var sourceRoot = output.resolveSibling(output.getFileName() + "-sources");
        final var files = new ArrayList<File>();
        for (final var source : sources.entrySet()) {
            final var file = sourceRoot.resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            files.add(file.toFile());
        }
        final var classpath = dependencies.stream().map(Path::toString).collect(joining(File.pathSeparator));
        final var options = new ArrayList<>(List.of("--release", "17", "-classpath",
                (classpath.isEmpty() ? "" : classpath + File.pathSeparator) + System.getProperty("java.class.path"),
                "-d", output.toString(), "-s", sourceRoot.resolve("generated").toString()));
        if (processing) {
            options.addAll(List.of("-Afusion.generateNativeImage=false",
                    "-Afusion.workdir=" + output.resolveSibling(output.getFileName() + "-cache")));
            if (separateProcessorPath) {
                options.addAll(List.of("-processorpath", System.getProperty("java.class.path"),
                        "-processor", FusionProcessor.class.getName(), "-Afusion.skipLoadingModules=true"));
            }
        } else {
            options.add("-proc:none");
        }
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new StringWriter();
        final var thread = Thread.currentThread();
        final var previous = thread.getContextClassLoader();
        // Dependency aliases are on javac's classpath only, not the processor loader.
        // Handwritten providers, by contrast, deliberately belong to the processor path.
        try (final var loader = loader(dependencies.stream()
                     .filter(it -> Files.isDirectory(it) && Files.exists(it.resolve("META-INF/services/" + MetadataContributor.class.getName())))
                     .toList());
             final var manager = compiler.getStandardFileManager(null, null, null)) {
            thread.setContextClassLoader(loader);
            final var task = compiler.getTask(diagnostics, manager, null, options, null, manager.getJavaFileObjectsFromFiles(files));
            if (processing && !separateProcessorPath) {
                final var internals = new ProcessorInternals(loader);
                task.setProcessors(List.of((Processor) internals.loadClass(
                        "io.yupiik.fusion.framework.processor.internal.InternalFusionProcessor").getConstructor().newInstance()));
            }
            assertTrue(task.call(), diagnostics.toString());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private URLClassLoader loader(final List<Path> paths) throws Exception {
        final var urls = new ArrayList<URL>();
        for (final var path : paths) {
            urls.add(path.toUri().toURL());
        }
        return new URLClassLoader(urls.toArray(URL[]::new), getClass().getClassLoader());
    }

    private void assertNoDescriptors(final Path output) {
        assertFalse(Files.exists(output.resolve(DESCRIPTORS)));
        assertFalse(Files.exists(output.resolveSibling(output.getFileName() + "-cache").resolve("metadata-aliases-v1.bin")));
        assertFalse(Files.exists(output.resolveSibling(".fusion-" + output.getFileName()).resolve("metadata-aliases-origins-v1")));
    }
}
