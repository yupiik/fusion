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
package io.yupiik.fusion.testing.module;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingPredicateModuleTest {
    @TempDir
    Path work;

    @Test
    void automaticModuleRegistrationsAreNotFilteredAndSameTypeDefinitionsRemainDistinct() throws Exception {
        compileModule();
        service("fixtures.GeneratedModule");
        withLoader(List.of(work), () -> {
            final var fallback = new ListingPredicateModule(name -> false, name -> false, List.of(work));
            try (final var container = ConfiguringContainer.of().register(fallback).start();
                 final var values = container.lookups(String.class)) {
                assertEquals(List.of("one", "two"), values.instance());
                final var events = new ArrayList<String>();
                container.emit(events);
                assertEquals(List.of("one", "two"), events);
                assertEquals(0, fallback.beans().count());
                assertEquals(0, fallback.listeners().count());
            }
        });
    }

    @Test
    void legacyFallbackWorksWithoutModuleAndDoesNotScanDependenciesOrDuplicateDirectories() throws Exception {
        final var local = work.resolve("local");
        Files.createDirectories(local);
        copyClass(Other$FusionBean.class, work.resolve("dependency"));
        copyClass(Manual$FusionBean.class, local);
        copyClass(Manual$FusionListener$event.class, local);
        withLoader(List.of(local, work.resolve("dependency")), () -> {
            final var fallback = legacy(local);
            assertEquals(List.of(Long.class), fallback.beans().map(FusionBean::type).toList());
            assertEquals(2, fallback.listeners().count());
            try (final var container = ConfiguringContainer.of().register(fallback).start();
                 final var value = container.lookup(Long.class)) {
                assertEquals(Long.valueOf(1), value.instance());
                final var events = new ArrayList<String>();
                container.emit(events);
                assertEquals(List.of("legacy"), events);
                assertEquals(0, fallback.beans().count());
                assertEquals(0, fallback.listeners().count());
            }
        });
    }

    @Test
    void distinctLegacyClassesWithSameBusinessTypeArePreserved() throws Exception {
        copyClass(Manual$FusionBean.class, work);
        copyClass(Other$FusionBean.class, work);
        withLoader(List.of(work), () -> {
            try (final var container = ConfiguringContainer.of().register(legacy(work)).start();
                 final var values = container.lookups(Long.class)) {
                assertEquals(List.of(1L, 2L), values.instance().stream().sorted().toList());
            }
        });
    }

    @Test
    void automaticLegacyModuleOwnsItsDescriptorsWithoutDuplicateFallbacks() throws Exception {
        copyClass(Manual$FusionBean.class, work);
        copyClass(Manual$FusionListener$event.class, work);
        service(LegacyModule.class.getName());
        withLoader(List.of(work), () -> {
            final var fallback = legacy(work);
            try (final var container = ConfiguringContainer.of().register(fallback).start();
                 final var values = container.lookups(Long.class)) {
                assertEquals(List.of(1L), values.instance());
                final var events = new ArrayList<String>();
                container.emit(events);
                assertEquals(List.of("legacy"), events);
            }
        });
    }

    @Test
    void filenamePredicatesOnlySelectLegacyFallbacksAndNeverInvokeFactories() throws Exception {
        compileModule();
        copyClass(Manual$FusionBean.class, work);
        copyClass(Manual$FusionListener$event.class, work);
        // Even accepting every filename must not invoke a descriptor factory.
        withLoader(List.of(work), () -> {
            final var all = new ListingPredicateModule(name -> true, name -> true, List.of(work));
            assertEquals(List.of(Long.class), all.beans().map(FusionBean::type).toList());
            final var selected = new ListingPredicateModule(name -> name.endsWith("Manual$FusionBean.class"),
                    name -> false, List.of(work));
            assertEquals(1, selected.beans().count());
            assertEquals(1, selected.listeners().count()); // startup cleanup only
        });
    }

    @Test
    void servicesAreNotResolvedByFallback() throws Exception {
        copyClass(Manual$FusionBean.class, work);
        service(TestingModule.class.getName() + "\nmissing.UnrelatedModule");
        withLoader(List.of(work), () -> assertEquals(1, legacy(work).beans().count()));
    }

    @Test
    void explicitTestingDirectoriesOverrideBuildLayoutAndUsePlatformPathSeparator() throws Exception {
        final var first = work.resolve("first.output");
        final var second = work.resolve("second.output");
        copyClass(Manual$FusionBean.class, first);
        copyClass(Other$FusionBean.class, second);
        final var executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        final var process = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"),
                "-D" + TestingModule.class.getName() + ".dirs=" + first + File.pathSeparator + second,
                DiscoveryMain.class.getName()).redirectErrorStream(true).start();
        try (final var in = process.getInputStream()) {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Scoped discovery subprocess did not terminate");
            final var output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertEquals("2", output.strip());
        } finally {
            process.destroyForcibly();
        }
    }

    private ListingPredicateModule legacy(final Path dir) {
        return new ListingPredicateModule(name -> name.endsWith("$FusionBean.class"),
                name -> name.contains("$FusionListener$"), List.of(dir, dir));
    }

    private void compileModule() throws Exception {
        final var source = work.resolve("GeneratedModule.java");
        Files.writeString(source, """
                package fixtures;
                import io.yupiik.fusion.framework.api.container.*;
                import io.yupiik.fusion.framework.api.scope.DefaultScoped;
                import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
                import io.yupiik.fusion.framework.api.container.listener.FunctionalListener;
                import java.util.*;
                import java.util.stream.Stream;
                public class GeneratedModule implements FusionModule {
                    public Stream<FusionBean<?>> beans() {
                        return Stream.of(
                            new FunctionalBean<>(String.class, DefaultScoped.class, 1000, 1000, Map.of(),
                                (c, d) -> "one", null, null, null, false),
                            new FunctionalBean<>(String.class, DefaultScoped.class, 1000, 1000, Map.of(),
                                (c, d) -> "two", null, null, null, false));
                    }
                    public Stream<FusionListener<?>> listeners() {
                        return Stream.of(
                            new FunctionalListener<List<String>>(ArrayList.class, 1000, (c, e) -> e.add("one")),
                            new FunctionalListener<List<String>>(ArrayList.class, 1000, (c, e) -> e.add("two")));
                    }
                }
                """);
        final var factories = work.resolve("FusionFactories.java");
        Files.writeString(factories, """
                package fixtures;
                import io.yupiik.fusion.framework.api.container.FusionBean;
                public class FusionFactories {
                    public static FusionBean<?> Unused$FusionBean() {
                        throw new AssertionError("Descriptor factories must not be scanned");
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-proc:none", "-classpath", System.getProperty("java.class.path"), "-d", work.toString(),
                source.toString(), factories.toString()));
    }

    private void service(final String providers) throws IOException {
        final var service = work.resolve("META-INF/services/" + FusionModule.class.getName());
        Files.createDirectories(service.getParent());
        Files.writeString(service, providers + "\n");
    }

    private void copyClass(final Class<?> type, final Path root) throws Exception {
        final var resource = type.getName().replace('.', '/') + ".class";
        final var file = root.resolve(resource);
        Files.createDirectories(file.getParent());
        try (final var in = getClass().getClassLoader().getResourceAsStream(resource)) {
            Files.copy(in, file);
        }
    }

    private void withLoader(final List<Path> roots, final Runnable test) throws Exception {
        final var urls = new URL[roots.size()];
        for (int i = 0; i < roots.size(); i++) {
            urls[i] = roots.get(i).toUri().toURL();
        }
        final var thread = Thread.currentThread();
        final var previous = thread.getContextClassLoader();
        try (final var loader = new URLClassLoader(urls, previous) {
            @Override
            public Enumeration<URL> getResources(final String name) throws IOException {
                // Isolate module discovery from unrelated modules on the test runner's classpath.
                return name.equals("META-INF/services/" + FusionModule.class.getName()) ?
                        findResources(name) : super.getResources(name);
            }
        }) {
            thread.setContextClassLoader(loader);
            test.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    public static class DiscoveryMain {
        public static void main(final String[] args) {
            System.out.println(new TestingModule().beans().count());
        }
    }

    public static class LegacyModule implements FusionModule {
        @Override
        public Stream<FusionBean<?>> beans() {
            return Stream.of(new Manual$FusionBean());
        }

        @Override
        public Stream<FusionListener<?>> listeners() {
            return Stream.of(new Manual$FusionListener$event());
        }
    }

    public static class Manual$FusionListener$event implements FusionListener<ArrayList<String>> {
        @Override
        public Class<?> eventType() {
            return ArrayList.class;
        }

        @Override
        public void onEvent(final RuntimeContainer container, final ArrayList<String> event) {
            event.add("legacy");
        }
    }

    public static class Manual$FusionBean extends BaseBean<Long> {
        public Manual$FusionBean() {
            super(Long.class, DefaultScoped.class, 1000, Map.of());
        }

        @Override
        public Long create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return 1L;
        }
    }

    public static class Other$FusionBean extends BaseBean<Long> {
        public Other$FusionBean() {
            super(Long.class, DefaultScoped.class, 1000, Map.of());
        }

        @Override
        public Long create(final RuntimeContainer container, final List<Instance<?>> dependents) {
            return 2L;
        }
    }
}
