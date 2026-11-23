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

import io.yupiik.fusion.framework.processor.test.ProcessorInternals;
import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompilerSourceTest {
    @TempDir
    Path work;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void selectsCompilerIntegrationAndResolvesActualCompilationUnits(final boolean ecj) throws Exception {
        final var compiler = ecj ? new EclipseCompiler() : ToolProvider.getSystemJavaCompiler();
        final var source = work.resolve("source with spaces/demo/Owner.java");
        final var record = source.resolveSibling("Model.java");
        final var generated = Files.createDirectories(work.resolve("generated"));
        final var classes = Files.createDirectories(work.resolve("classes"));
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package demo;
                public class Owner {
                    public String field;
                    public Owner(String input) { field = input; }
                    public static class Nested {}
                }
                class Secondary {}
                """);
        Files.writeString(record, "package demo; public record Model(int value) {}");
        final var diagnostics = new StringWriter();
        final SourceProbe probe;
        try (final var packaged = packagedProcessor(ecj);
             final var manager = compiler.getStandardFileManager(null, null, null)) {
            // Use the production loader with a real MR-JAR, not exploded target/classes.
            final var loaderType = Class.forName(FusionProcessor.class.getName() + "$InternalClassLoader");
            final var constructor = loaderType.getDeclaredConstructor(ClassLoader.class);
            constructor.setAccessible(true);
            probe = new SourceProbe((ClassLoader) constructor.newInstance(packaged));
            final var task = compiler.getTask(diagnostics, manager, null, List.of(
                    "--release", "17", "-proc:only", ecj ? "-failOnWarning" : "-Werror",
                    "-classpath", System.getProperty("java.class.path"),
                    "-d", classes.toString(), "-s", generated.toString()), null,
                    manager.getJavaFileObjectsFromPaths(List.of(source, record)));
            task.setProcessors(List.of(probe));
            final var success = task.call();
            if (probe.failure != null) {
                throw new AssertionError(diagnostics.toString(), probe.failure);
            }
            assertTrue(success, diagnostics.toString());
        }
        assertEquals(Runtime.version().feature() > 17 ? "Standard" : ecj ? "Ecj" : "Javac", probe.implementation);
        assertEquals(Set.of("owner", "nested", "secondary", "field", "constructor", "record", "component", "parameter", "generated"),
                probe.locations.keySet());
        for (final var entry : probe.locations.entrySet()) {
            final var expected = switch (entry.getKey()) {
                case "record", "component", "parameter" -> record;
                case "generated" -> generated.resolve("demo/Generated.java");
                default -> source;
            };
            assertEquals(expected.toRealPath(), Path.of(URI.create(entry.getValue()
                    .orElseThrow(() -> new AssertionError("Missing source for " + entry.getKey())))).toRealPath(), entry.getKey());
        }
        assertTrue(probe.binaryLocation.isEmpty(), "A class file must not be recorded as a source origin");
    }

    private URLClassLoader packagedProcessor(final boolean ecj) throws Exception {
        final var output = Path.of(FusionProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final var jar = work.resolve("processor.jar");
        final var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        try (final var out = new JarOutputStream(Files.newOutputStream(jar), manifest);
             final var files = Files.walk(output)) {
            for (final var file : files.filter(Files::isRegularFile).toList()) {
                final var name = output.relativize(file).toString().replace('\\', '/');
                if (!name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    out.putNextEntry(new JarEntry(name));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }
        }
        final var resource = "fusion/annotationprocessor/precompiled/io/yupiik/fusion/framework/processor/internal/CompilerSourceFactory.classF";
        try (final var packaged = new JarFile(jar.toFile())) {
            assertTrue(packaged.isMultiRelease());
            assertTrue(packaged.getEntry(resource) != null, "Missing Java 17 factory");
            assertTrue(packaged.getEntry("META-INF/versions/18/" + resource) != null, "Missing relocated Java 18 factory");
            assertTrue(packaged.stream().noneMatch(it -> it.getName().contains("/internal/") && it.getName().endsWith(".class")));
        }
        return new URLClassLoader(new URL[]{jar.toUri().toURL()}, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                if (!ecj && name.startsWith("org.eclipse.jdt.")) {
                    throw new ClassNotFoundException("ECJ is deliberately absent from this javac-only loader: " + name);
                }
                return super.loadClass(name, resolve);
            }

            @Override
            public URL getResource(final String name) {
                if (name.startsWith("fusion/annotationprocessor/precompiled/")) {
                    return findResource(name);
                }
                return super.getResource(name);
            }
        };
    }

    private static final class SourceProbe extends AbstractProcessor {
        private final ClassLoader loader;
        private final Map<String, Optional<String>> locations = new HashMap<>();
        private Object source;
        private Method uri;
        private String implementation;
        private Optional<String> binaryLocation;
        private Throwable failure;

        private SourceProbe(final ClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public void init(final ProcessingEnvironment environment) {
            super.init(environment);
            try {
                final var type = loader.loadClass("io.yupiik.fusion.framework.processor.internal.CompilerSource");
                source = ProcessorInternals.invoke(type.getMethod("of", ProcessingEnvironment.class), null, environment);
                uri = type.getMethod("uri", Element.class);
                implementation = source.getClass().getSimpleName();
            } catch (final Exception | LinkageError e) {
                failure = e;
                throw new IllegalStateException(e);
            }
        }

        @Override
        public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment round) {
            if (round.processingOver()) {
                return false;
            }
            try {
                if (locations.isEmpty()) {
                    final var elements = processingEnv.getElementUtils();
                    final var owner = elements.getTypeElement("demo.Owner");
                    locations.put("owner", uri(source, owner));
                    locations.put("nested", uri(source, elements.getTypeElement("demo.Owner.Nested")));
                    locations.put("secondary", uri(source, elements.getTypeElement("demo.Secondary")));
                    locations.put("field", uri(source, owner.getEnclosedElements().stream()
                            .filter(it -> it.getKind() == ElementKind.FIELD).findFirst().orElseThrow()));
                    locations.put("constructor", uri(source, owner.getEnclosedElements().stream()
                            .filter(it -> it.getKind() == ElementKind.CONSTRUCTOR).findFirst().orElseThrow()));
                    final var record = elements.getTypeElement("demo.Model");
                    locations.put("record", uri(source, record));
                    locations.put("component", uri(source, record.getRecordComponents().get(0)));
                    locations.put("parameter", uri(source, record.getEnclosedElements().stream()
                            .filter(it -> it.getKind() == ElementKind.CONSTRUCTOR)
                            .map(ExecutableElement.class::cast)
                            .findFirst().orElseThrow().getParameters().get(0)));
                    binaryLocation = uri(source, elements.getTypeElement("java.lang.String"));
                    if (Runtime.version().feature() > 17) {
                        assertStandardFileObjectContract(elements, owner);
                    } else if (implementation.equals("Ecj")) {
                        assertEcjFileContract(owner);
                    }
                    try (final var out = processingEnv.getFiler().createSourceFile("demo.Generated").openWriter()) {
                        out.write("package demo; public class Generated {}");
                    }
                } else {
                    final var generated = processingEnv.getElementUtils().getTypeElement("demo.Generated");
                    if (generated != null) {
                        locations.put("generated", uri(source, generated));
                    }
                }
                return false;
            } catch (final Exception | LinkageError e) {
                failure = e;
                throw new IllegalStateException(e);
            }
        }

        private Optional<String> uri(final Object integration, final Element element) throws Exception {
            return ((Optional<?>) ProcessorInternals.invoke(uri, integration, element)).map(Object::toString);
        }

        private void assertEcjFileContract(final Element element) throws Exception {
            final var path = Path.of(URI.create(uri(source, element).orElseThrow()));
            final var content = Files.readString(path);
            try {
                Files.delete(path);
                assertTrue(uri(source, element).isEmpty(), "A missing source file is unverifiable");
                Files.createDirectory(path);
                assertTrue(uri(source, element).isEmpty(), "A directory is not a source file");
            } finally {
                Files.deleteIfExists(path);
                Files.writeString(path, content);
            }
        }

        private void assertStandardFileObjectContract(final Elements elements, final Element element) throws Exception {
            for (final var kind : List.of(JavaFileObject.Kind.SOURCE, JavaFileObject.Kind.CLASS)) {
                final var file = new SimpleJavaFileObject(URI.create("memory:///Example" + kind.extension), kind) {};
                final var integration = withFileObject(elements, file, false);
                assertEquals(kind == JavaFileObject.Kind.SOURCE ? Optional.of(file.toUri().toString()) : Optional.empty(),
                        uri(integration, element));
            }
            assertTrue(uri(withFileObject(elements, null, false), element).isEmpty());
            assertTrue(uri(withFileObject(elements, null, true), element).isEmpty());
        }

        private Object withFileObject(final Elements elements, final JavaFileObject file, final boolean unsupported) throws Exception {
            final var wrappedElements = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Elements.class}, (proxy, method, args) -> {
                if (method.getName().equals("getFileObjectOf")) {
                    if (unsupported) {
                        throw new UnsupportedOperationException();
                    }
                    return file;
                }
                return ProcessorInternals.invoke(method, elements, args);
            });
            final var wrappedEnvironment = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ProcessingEnvironment.class},
                    (proxy, method, args) -> method.getName().equals("getElementUtils") ? wrappedElements :
                            ProcessorInternals.invoke(method, processingEnv, args));
            return ProcessorInternals.invoke(uri.getDeclaringClass().getMethod("of", ProcessingEnvironment.class),
                    null, wrappedEnvironment);
        }
    }
}
