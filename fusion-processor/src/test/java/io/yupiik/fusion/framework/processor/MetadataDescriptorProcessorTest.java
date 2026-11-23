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

import io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias;
import io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor;
import io.yupiik.fusion.framework.processor.test.ProcessorInternals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataDescriptorProcessorTest {
    @TempDir
    Path work;
    @Test
    void compilerModelComputesAliasesOnAnnotationTypesMethodsAndParameters() throws Exception {
        final var source = work.resolve("Examples.java");
        Files.writeString(source, """
                package demo;
                import io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias;
                public class Examples {
                    @BeanMetadataAlias(name="kind", value="fallback")
                    public @interface Flag { Class<?> value() default String.class; }
                    @Flag(Integer.class) public @interface Meta {}
                    @Flag(Long.class) public void run(@Flag(Double.class) String argument) {}
                }
                """);
        final var registry = new ProcessorInternals(getClass().getClassLoader()).loadClass(
                "io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry");
        final var actual = new HashMap<String, Object>();
        final var processor = new AbstractProcessor() {
            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return Set.of("*");
            }

            @Override
            public SourceVersion getSupportedSourceVersion() {
                return SourceVersion.RELEASE_17;
            }

            @Override
            public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
                if (roundEnv.processingOver()) {
                    return false;
                }
                try {
                    final var instance = registry.getConstructor(ProcessingEnvironment.class).newInstance(processingEnv);
                    final var compute = registry.getMethod("compute", Element.class);
                    final var type = processingEnv.getElementUtils().getTypeElement("demo.Examples");
                    for (final var element : type.getEnclosedElements()) {
                        if (element.getSimpleName().contentEquals("Meta")) {
                            actual.put("annotation", ProcessorInternals.invoke(compute, instance, element));
                        } else if (element instanceof ExecutableElement method && method.getSimpleName().contentEquals("run")) {
                            actual.put("method", ProcessorInternals.invoke(compute, instance, method));
                            actual.put("parameter", ProcessorInternals.invoke(compute, instance, method.getParameters().get(0)));
                        }
                    }
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
                return false;
            }
        };
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(diagnostics, manager, null,
                    List.of("--release", "17", "-classpath", System.getProperty("java.class.path"),
                            "-d", Files.createDirectories(work.resolve("classes")).toString()),
                    null, manager.getJavaFileObjects(source));
            task.setProcessors(List.of(processor));
            assertTrue(task.call(), diagnostics.toString());
        }
        assertEquals(Map.of("annotation", Map.of("kind", "java.lang.Integer.class"),
                "method", Map.of("kind", "java.lang.Long.class"),
                "parameter", Map.of("kind", "java.lang.Double.class")), actual);
    }


    @Test
    void compilerModelReadsNestedAliasesWithoutGeneratingImplementationsOrResources() throws Exception {
        final var annotation = work.resolve("Aliases.java");
        Files.writeString(annotation, """
                package demo;
                import io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias;
                public class Aliases {
                    @BeanMetadataAlias(name="quote\\\"\\n", value="slash\\\\\\t")
                    public @interface Constant {}
                    @BeanMetadataAlias
                    public @interface Dynamic {
                        String name();
                        String value();
                    }
                    @BeanMetadataAlias public @interface Invalid {}
                    @BeanMetadataAlias(name="only-name") public @interface InvalidValue {}
                }
                """);
        final var generator = new ProcessorInternals(getClass().getClassLoader()).loadClass(
                "io.yupiik.fusion.framework.processor.internal.generator.MetadataContributorGenerator");
        final var aliases = new ArrayList<Map<String, String>>();
        final var processor = new AbstractProcessor() {
            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return Set.of(BeanMetadataAlias.class.getName());
            }

            @Override
            public SourceVersion getSupportedSourceVersion() {
                return SourceVersion.RELEASE_17;
            }

            @Override
            public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
                for (final var element : roundEnv.getElementsAnnotatedWith(BeanMetadataAlias.class)) {
                    try {
                        final var instance = generator.getConstructor(Elements.class, TypeElement.class)
                                .newInstance(processingEnv.getElementUtils(), element);
                        if (element.getSimpleName().toString().startsWith("Invalid")) {
                            final var error = assertThrows(IllegalArgumentException.class,
                                    () -> ProcessorInternals.invoke(generator.getMethod("get"), instance));
                            assertTrue(error.getMessage().contains(element.getSimpleName().contentEquals("Invalid") ?
                                    "does not define name()" : "does not define value()"));
                            // Annotation's inherited methods are present, but cannot satisfy name()/value().
                            assertTrue(processingEnv.getElementUtils().getAllMembers((TypeElement) element).stream()
                                    .anyMatch(it -> it.getSimpleName().contentEquals("annotationType")));
                        } else {
                            final var contributor = (MetadataContributor) ProcessorInternals.invoke(generator.getMethod("get"), instance);
                            aliases.add(Map.of("type", contributor.annotationType(), "name", contributor.name(), "value", contributor.value()));
                        }
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                return false;
            }
        };
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new StringWriter();
        final var output = Files.createDirectories(work.resolve("classes"));
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(diagnostics, manager, null,
                    List.of("--release", "17", "-classpath", System.getProperty("java.class.path"), "-d", output.toString()),
                    null, manager.getJavaFileObjects(annotation));
            task.setProcessors(List.of(processor));
            assertTrue(task.call(), diagnostics.toString());
        }
        assertEquals(List.of(
                Map.of("type", "demo.Aliases.Constant", "name", "quote\"\n", "value", "slash\\\t"),
                Map.of("type", "demo.Aliases.Dynamic", "name", "", "value", "")), aliases);
        try (final var files = Files.walk(output)) {
            assertEquals(5, files.filter(Files::isRegularFile).count());
        }
        assertFalse(Files.exists(output.resolve("META-INF/fusion/metadata/aliases-v1")));
        assertFalse(Files.exists(output.resolve("META-INF/services/" + MetadataContributor.class.getName())));
    }
}
