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

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
import io.yupiik.fusion.framework.api.container.listener.FunctionalListener;
import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactProcessorTest {
    @TempDir
    Path work;

    @Test
    void ordinaryOnePackageBeanOnlyGeneratesTheModule() throws Exception {
        compile(Map.of("demo.A", bean("A")));
        try (final var files = Files.walk(work.resolve("generated"))) {
            assertEquals(List.of("demo/Module.java"), files.filter(Files::isRegularFile)
                    .map(it -> work.resolve("generated").relativize(it).toString().replace('\\', '/')).sorted().toList());
        }
        try (final var files = Files.walk(work.resolve("classes"))) {
            assertEquals(List.of("demo/A.class", "demo/Module.class"), files.filter(it -> it.toString().endsWith(".class"))
                    .map(it -> work.resolve("classes").relativize(it).toString().replace('\\', '/')).sorted().toList());
        }
        assertTrue(holderNames().isEmpty());
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(source.contains("builder.add(new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<"), source);
        assertFalse(source.contains("$definition"), source);
        assertFalse(source.contains(".copy()"), source);
        assertFalse(source.contains(" INSTANCE"), source);
        assertFalse(source.contains("extends io.yupiik.fusion.framework.api.container.BaseLookup"), source);
        try (final var loader = loader()) {
            final var module = module(loader);
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start();
                 final var instance = container.lookup(loader.loadClass("demo.A"))) {
                assertEquals("demo.A", instance.instance().getClass().getName());
            }
        }
    }

    @Test
    void nonDescriptorConstructionAndItsHelpersBelongToTheModule() throws Exception {
        compile(Map.of("demo.Model", """
                package demo;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Model(String value) {}
                """));
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(source.contains("new io.yupiik.fusion.json.serialization.MetadataJsonCodec"), source);
        assertTrue(source.contains("new demo.Model("), source);
        assertEquals(1, source.lines().filter(it -> it.contains("new demo.Model(")).count(), source);
        assertTrue(source.contains("private static"), source);
        assertFalse(source.contains("FusionFactories"), source);
        assertFalse(source.contains("FusionBindings_"), source);
        assertFalse(source.contains("return demo.Module.Model$FusionJsonCodec()"), source);
        assertFalse(source.contains(" INSTANCE"), source);
        assertTrue(holderNames().isEmpty());
        try (final var loader = loader()) {
            final var first = module(loader).beans().findFirst().orElseThrow();
            final var second = module(loader).beans().findFirst().orElseThrow();
            assertNotSame(first, second);
            assertNotSame(first.create(null, new ArrayList<>()), second.create(null, new ArrayList<>()));
            assertEquals(1, GeneratedFactories.methods(loader, "demo", io.yupiik.fusion.json.serialization.JsonCodec.class).size());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void arbitraryConfigurationDefaultsShareOneModuleOwnedFragment() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(@Property(defaultValue="Integer.parseInt(this.configuration.get(this.keyMapper.apply(\\\"fallback\\\")).orElse(\\\"23\\\"))") int count) {}
                """));
        try (final var files = Files.walk(work.resolve("generated"))) {
            assertEquals(List.of("demo/Module.java"), files.filter(Files::isRegularFile)
                    .map(it -> work.resolve("generated").relativize(it).toString().replace('\\', '/')).sorted().toList());
        }
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertFalse(source.contains(" INSTANCE"), source);
        assertTrue(source.contains("private Module(final io.yupiik.fusion.framework.api.configuration.Configuration"), source);
        assertTrue(source.contains("public Module()"), source);
        assertEquals(1, source.lines().filter(it -> it.contains("new demo.Settings(")).count(), source);
        assertEquals(1, source.lines().filter(it -> it.contains("_default() { return")).count(), source);
        try (final var loader = loader()) {
            final var factory = (java.util.function.BiFunction<io.yupiik.fusion.framework.api.configuration.Configuration,
                    java.util.function.Function<String, String>, ?>) loader.loadClass("demo.Module")
                    .getMethod("Settings$FusionConfigurationFactory").invoke(null);
            final io.yupiik.fusion.framework.api.configuration.Configuration configuration =
                    key -> java.util.Optional.ofNullable(Map.of("mapped-fallback", "37").get(key));
            final var settings = factory.apply(configuration, key -> "mapped-" + key);
            assertEquals(37, settings.getClass().getMethod("count").invoke(settings));
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module(loader))
                    .register(new io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean<>(
                            io.yupiik.fusion.framework.api.scope.DefaultScoped.class,
                            io.yupiik.fusion.framework.api.configuration.ConfigurationSource.class, () -> key -> null)).start();
                 final var instance = container.lookup(loader.loadClass("demo.Settings"))) {
                assertEquals(23, instance.instance().getClass().getMethod("count").invoke(instance.instance()));
            }
        }
    }

    @Test
    void sizeOverflowKeepsStableModuleManualMethods() throws Exception {
        final var models = IntStream.range(0, 40).mapToObj(i ->
                "@io.yupiik.fusion.framework.build.api.json.JsonModel public record Model" + i +
                        "(" + IntStream.range(0, 12).mapToObj(field -> "String field" + field).collect(joining(", ")) + ") {}")
                .collect(joining("\n"));
        compile(Map.of("demo.Models", "package demo; public class Models {" + models + "}"),
                "-Afusion.generateBeanForJsonCodec=false");
        assertFalse(holderNames().isEmpty());
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(source.contains("return demo.FusionBindings_"), source);
        try (final var loader = loader()) {
            final var module = loader.loadClass("demo.Module");
            for (int index = 0; index < 40; index++) {
                final var method = module.getMethod("Models$Model" + index + "$FusionJsonCodec");
                assertInstanceOf(io.yupiik.fusion.json.serialization.JsonCodec.class, method.invoke(null));
                assertNotSame(method.invoke(null), method.invoke(null));
            }
        }
    }

    @Test
    void packageAccessUsesSiblingStreamsWithPrivateTypedHelpers() throws Exception {
        compile(Map.of("business.Service", """
                package business;
                @io.yupiik.fusion.framework.build.api.scanning.Bean
                class Service {
                    static String event;
                    Service(io.yupiik.fusion.framework.api.configuration.Configuration configuration) {
                        java.util.Objects.requireNonNull(configuration);
                    }
                    void receive(@io.yupiik.fusion.framework.build.api.event.OnEvent String value) { event = value; }
                }
                """, "business.Probe", """
                package business;
                public class Probe { public static String event() { return Service.event; } }
                """));
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(source.contains(".beans().forEachOrdered(builder::add)"), source);
        assertTrue(source.contains(".listeners().forEachOrdered(builder::add)"), source);
        assertFalse(source.contains("business.Service"), source);
        assertFalse(Files.exists(work.resolve("generated/business/FusionFactories.java")));
        for (final var file : holderSources()) {
            final var sibling = Files.readString(file);
            assertTrue(sibling.contains("builder.add(new io.yupiik.fusion.framework.api.container.bean.FunctionalBean") ||
                        sibling.contains("builder.add(new io.yupiik.fusion.framework.api.container.listener.FunctionalListener"), sibling);
            assertFalse(sibling.contains(" factory"), sibling);
            assertFalse(sibling.contains("public static business.Service"), sibling);
            assertFalse(sibling.contains(".copy()"), sibling);
        }
        try (final var loader = loader()) {
            final var module = module(loader);
            assertEquals(List.of("business.Service"), module.beans().map(it -> it.type().getTypeName()).toList());
            assertEquals(1, module.listeners().count());
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start()) {
                container.emit("package-event");
                assertEquals("package-event", loader.loadClass("business.Probe").getMethod("event").invoke(null));
            }
        }
    }

    @Test
    void reusingAModuleKeepsContainerInstancesLocksAndListenersIndependent() throws Exception {
        compile(Map.of("demo.Service", """
                package demo;
                @io.yupiik.fusion.framework.api.scope.ApplicationScoped
                public class Service {
                    private static int sequence;
                    private static final StringBuilder CLOSED = new StringBuilder();
                    private int id;
                    private int events;
                    @io.yupiik.fusion.framework.build.api.lifecycle.Init
                    public void init() { id = ++sequence; }
                    public int id() { return id; }
                    public int events() { return events; }
                    public static String closed() { return CLOSED.toString(); }
                    public void receive(@io.yupiik.fusion.framework.build.api.event.OnEvent String value) { events++; }
                    @io.yupiik.fusion.framework.build.api.lifecycle.Destroy
                    public void close() { CLOSED.append(id).append(';'); }
                }
                """));
        try (final var loader = loader()) {
            final var module = module(loader);
            final var firstBean = assertInstanceOf(FunctionalBean.class, module.beans().findFirst().orElseThrow());
            final var secondBean = assertInstanceOf(FunctionalBean.class, module.beans().findFirst().orElseThrow());
            assertNotSame(firstBean, secondBean);
            assertNotSame(firstBean.getLock(), secondBean.getLock());
            assertNotSame(module.listeners().findFirst().orElseThrow(), module.listeners().findFirst().orElseThrow());
            final var type = loader.loadClass("demo.Service");
            try (final var second = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start()) {
                try (final var first = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start();
                     final var firstInstance = first.lookup(type);
                     final var secondInstance = second.lookup(type)) {
                    assertNotSame(firstInstance.bean(), secondInstance.bean());
                    assertNotSame(assertInstanceOf(FunctionalBean.class, firstInstance.bean()).getLock(),
                            assertInstanceOf(FunctionalBean.class, secondInstance.bean()).getLock());
                    assertEquals(1, type.getMethod("id").invoke(firstInstance.instance()));
                    assertEquals(2, type.getMethod("id").invoke(secondInstance.instance()));
                    first.emit("first");
                    assertEquals(1, type.getMethod("events").invoke(firstInstance.instance()));
                    assertEquals(0, type.getMethod("events").invoke(secondInstance.instance()));
                    second.emit("second");
                    assertEquals(1, type.getMethod("events").invoke(secondInstance.instance()));
                }
                assertEquals("1;", type.getMethod("closed").invoke(null));
                try (final var instance = second.lookup(type)) {
                    assertEquals(2, type.getMethod("id").invoke(instance.instance()));
                    second.emit("still-running");
                    assertEquals(2, type.getMethod("events").invoke(instance.instance()));
                }
            }
            assertEquals("1;2;", type.getMethod("closed").invoke(null));
        }
    }
    @Test
    void typedLifecycleListenersAndLazyCallbacks() throws Exception {
        compile(Map.of("demo.App", """
                package demo;
                import io.yupiik.fusion.framework.api.scope.*;
                import io.yupiik.fusion.framework.build.api.scanning.*;
                import io.yupiik.fusion.framework.build.api.lifecycle.*;
                import io.yupiik.fusion.framework.build.api.event.*;
                public class App {
                    private static final StringBuilder TRACE = new StringBuilder();
                    public static String trace() { return TRACE.toString(); }
                    @DefaultScoped public static class First {
                        @Destroy public void stop() { TRACE.append("first;"); }
                    }
                    @DefaultScoped public static class Second {
                        @Destroy public void stop() { TRACE.append("second;"); }
                    }
                    @DefaultScoped public static class Service {
                        private final First first;
                        @Injection Second second;
                        public Service(First first) { this.first = first; TRACE.append("constructor;"); }
                        @Init public void init() { if (first == null || second == null) { throw new IllegalStateException(); } TRACE.append("init;"); }
                        @Destroy(order = -3) public void stop() { TRACE.append("service;"); }
                    }
                    @DefaultScoped public static class Producer {
                        @Bean public java.util.List<String> names() { return java.util.List.of("one"); }
                        @Bean public Resource resource() { TRACE.append("produce;"); return new Resource(); }
                        @Destroy public void stop() { TRACE.append("producer;"); }
                    }
                    public static class Resource implements AutoCloseable {
                        public void close() { TRACE.append("resource;"); }
                    }
                    @DefaultScoped public static class Receiver {
                        public void on(@OnEvent String event, java.util.List<First> first, java.util.Set<Second> second) {
                            TRACE.append("event;");
                            if (first.size() != 1 || second.size() != 1) { throw new IllegalStateException(); }
                            if (event.equals("fail")) { throw new IllegalArgumentException("listener failure"); }
                        }
                        @Destroy public void stop() { TRACE.append("receiver;"); }
                    }
                    @ApplicationScoped public static class Lazy {
                        @Init public void init() { TRACE.append("lazy;"); }
                        public String value() { return "lazy-value"; }
                    }
                }
                """));
        try (final var loader = loader()) {
            final var module = module(loader);
            final var beans = module.beans().toList();
            assertTrue(beans.stream().allMatch(FunctionalBean.class::isInstance));
            assertEquals(beans.size(), beans.stream().distinct().count());
            assertEquals(1, module.listeners().count());
            assertInstanceOf(FunctionalListener.class, module.listeners().findFirst().orElseThrow());
            assertEquals(List.of(String.class), module.listeners().map(it -> it.eventType()).toList());
            final var service = beans.stream().filter(it -> it.type().getTypeName().equals("demo.App$Service")).findFirst().orElseThrow();
            assertEquals(-3, service.destroyPriority());
            assertEquals(loader.loadClass("demo.App$Service"), service.type());
            assertNotEquals(service, module.beans().filter(it -> it.type().equals(service.type())).findFirst().orElseThrow());
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start()) {
                final var app = loader.loadClass("demo.App");
                try (final var instance = container.lookup(loader.loadClass("demo.App$Service"))) {
                    assertEquals("constructor;init;", app.getMethod("trace").invoke(null));
                    assertSame(service.getClass(), instance.bean().getClass());
                }
                assertEquals("constructor;init;service;second;first;", app.getMethod("trace").invoke(null));
                final var type = new io.yupiik.fusion.framework.api.container.Types.ParameterizedTypeImpl(List.class, String.class);
                try (final var names = container.lookup(type)) {
                    assertEquals(List.of("one"), names.instance());
                }
                try (final var resource = container.lookup(loader.loadClass("demo.App$Resource"))) {
                    assertEquals("demo.App$Resource", resource.instance().getClass().getName());
                }
                container.emit("ok");
                assertThrows(IllegalArgumentException.class, () -> container.emit("fail"));
                final var beforeLazy = app.getMethod("trace").invoke(null).toString();
                assertTrue(beforeLazy.endsWith("produce;resource;producer;event;second;first;receiver;event;second;first;receiver;"), beforeLazy);
                try (final var lazy = container.lookup(loader.loadClass("demo.App$Lazy"))) {
                    assertEquals(beforeLazy, app.getMethod("trace").invoke(null));
                    assertEquals("lazy-value", loader.loadClass("demo.App$Lazy").getMethod("value").invoke(lazy.instance()));
                    assertEquals(beforeLazy + "lazy;", app.getMethod("trace").invoke(null));
                }
            }
        }
        final var generated = new StringBuilder();
        try (final var files = Files.walk(work.resolve("generated"))) {
            for (final var file : files.filter(it -> it.toString().endsWith(".java")).sorted().toList()) {
                generated.append(Files.readString(file));
            }
        }
        final var holder = generated.toString();
        assertTrue(holder.contains("private static demo.App.Service"), holder);
        assertTrue(holder.contains("demo.App$Lazy$FusionSubclass::new"), holder);
        assertTrue(holder.contains("::"), holder);
        assertTrue(holder.contains("io.yupiik.fusion.framework.api.container.internal.DependencyLookups.lookup(container,"), holder);
        assertFalse(holder.contains(" INSTANCE"), holder);
        assertFalse(holder.contains("extends io.yupiik.fusion.framework.api.container.BaseLookup"), holder);
        assertFalse(holder.contains("Object invoke("), holder);
        assertFalse(holder.contains("switch (operation)"), holder);
        assertFalse(holder.contains("switch (id)"), holder);
        assertFalse(Files.exists(work.resolve("classes/demo/App$Service$FusionBean.class")));
        assertTypedCallbacks("demo.Module");
        assertTrue(holderNames().isEmpty());
        assertFalse(Files.exists(work.resolve("generated/demo/FusionFactories.java")));
    }

    @Test
    void incrementalOriginsAnnotationRemovalDeletionAndNestedNames() throws Exception {
        compile(Map.of("demo.A", bean("A"), "demo.B", bean("B"), "demo.Nested", """
                package demo;
                public class Nested {
                    @io.yupiik.fusion.framework.build.api.scanning.Bean public static class Inner {}
                }
                """));
        assertEquals(3, beanCount());
        compile(Map.of("demo.B", bean("B")));
        assertEquals(3, beanCount());
        compile(Map.of("demo.B", "package demo; public class B {}", "demo.Nested", "package demo; public class Nested {}"));
        assertEquals(1, beanCount());
        Files.delete(work.resolve("sources/demo/A.java")); // leave the stale class file deliberately
        compile(Map.of("demo.B", "package demo; public class B {}"));
        assertEquals(0, beanCount());
    }

    @Test
    void missingCacheRequiresFullRebuildInsteadOfLosingUnseenMembers() throws Exception {
        compile(Map.of("demo.A", bean("A"), "demo.B", bean("B")));
        Files.delete(work.resolve("state/compact-v1.bin"));
        final var failure = assertThrows(AssertionError.class, () -> compile(Map.of("demo.B", bean("B"))));
        assertTrue(failure.getMessage().contains("full clean rebuild"), failure.getMessage());
    }

    @Test
    void obsoleteSerializedOperationsRequireCleanRebuild() throws Exception {
        compile(Map.of("demo.A", bean("A")));
        try (final var out = new java.io.DataOutputStream(Files.newOutputStream(work.resolve("state/compact-v1.bin")))) {
            out.writeInt(1);
            out.writeInt(0);
        }
        final var failure = assertThrows(RuntimeException.class, () -> compile(Map.of("demo.A", bean("A"))));
        assertTrue(failure.getCause().getMessage().contains("full clean rebuild"), failure.toString());
    }

    @Test
    void incompleteCacheIsNotAcceptedAsAnEmptyApplication() throws Exception {
        compile(Map.of("demo.A", bean("A"), "demo.B", bean("B")));
        try (final var out = new java.io.DataOutputStream(Files.newOutputStream(work.resolve("state/compact-v1.bin")))) {
            out.writeInt(2);
            out.writeInt(0);
        }
        final var failure = assertThrows(RuntimeException.class, () -> compile(Map.of("demo.B", bean("B"))));
        assertTrue(failure.getCause().getMessage().contains("full clean rebuild"), failure.toString());
    }

    @Test
    void holderRegistrationPreservesStableOrderAcrossInterleavedPackages() throws Exception {
        compile(Map.of(
                "demo.a", bean("a"),
                "demo.z", bean("z"),
                "demo.m.Middle", "package demo.m; @io.yupiik.fusion.framework.build.api.scanning.Bean public class Middle {}"),
                "-Afusion.moduleFqn=demo.Module");
        try (final var loader = loader()) {
            assertEquals(List.of("demo.a", "demo.m.Middle", "demo.z"),
                    module(loader).beans().map(it -> it.type().getTypeName()).toList());
        }
        final var generated = Files.readString(work.resolve("generated/demo/Module.java"));
        assertFalse(generated.contains(".factory("), generated);
        assertTrue(generated.contains("builder.add("), generated);
        assertFalse(generated.contains("builder, int from"), generated);
        assertFalse(generated.contains("beans0("), generated);
    }

    @Test
    void largeModuleUsesPrecomputedRegistrationChunks() throws Exception {
        final var classes = IntStream.range(0, 70)
                .mapToObj(it -> "@io.yupiik.fusion.framework.build.api.scanning.Bean public static class B" + it + " {}")
                .collect(joining("\n"));
        compile(Map.of("demo.Many", "package demo; public class Many {" + classes + "}"),
                "-Afusion.moduleFqn=demo.Module");
        final var generated = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(generated.contains("beans0(builder);"), generated);
        assertTrue(generated.contains("beans1(builder);"), generated);
        assertFalse(generated.contains("int from"), generated);
        assertFalse(generated.contains("int to"), generated);
        assertFalse(generated.contains("if ("), generated);
        try (final var loader = loader()) {
            assertEquals(70, module(loader).beans().count());
        }
    }

    @Test
    void moduleDisabledOmitsDescriptorFactoriesAndHelpers() throws Exception {
        final var classes = IntStream.range(0, 70)
                .mapToObj(it -> "@io.yupiik.fusion.framework.build.api.scanning.Bean public static class B" + it +
                        " { public B" + it + "(io.yupiik.fusion.framework.api.configuration.Configuration configuration) {" +
                        " java.util.Objects.requireNonNull(configuration); } }")
                .collect(joining("\n"));
        compile(Map.of("demo.Many", "package demo; public class Many {" + classes + "}"), "-Afusion.generateModule=false");
        try (final var files = Files.walk(work.resolve("generated"))) {
            assertTrue(files.noneMatch(Files::isRegularFile));
        }
        assertFalse(Files.exists(work.resolve("classes/demo/Module.class")));
        assertTrue(holderNames().isEmpty());
    }

    @Test
    void moduleCanBeEnabledUsingPreviouslyCachedDescriptors() throws Exception {
        final var classes = IntStream.range(0, 70)
                .mapToObj(it -> "@io.yupiik.fusion.framework.build.api.scanning.Bean public static class B" + it + " {}")
                .collect(joining("\n"));
        compile(Map.of("demo.Many", "package demo; public class Many {" + classes + "}"), "-Afusion.generateModule=false");
        assertFalse(Files.exists(work.resolve("generated/demo/FusionFactories.java")));
        compile(Map.of("demo.Other", "package demo; public class Other {}"));
        assertEquals(70, beanCount());
        assertFalse(Files.exists(work.resolve("generated/demo/FusionFactories.java")));
    }
    @Test
    void moduleDisabledKeepsStandaloneFactoriesButNoDescriptorCallbacks() throws Exception {
        compile(Map.of("demo.A", beanWithDependency("A"), "demo.Settings", """
                package demo;
                @io.yupiik.fusion.framework.build.api.configuration.RootConfiguration("settings")
                public record Settings(String name) {}
                """), "-Afusion.generateModule=false");
        final var facade = constructionSources();
        assertTrue(facade.contains("Settings$FusionConfigurationFactory()"), facade);
        assertFalse(facade.contains("FusionBean"), facade);
        try (final var files = Files.walk(work.resolve("generated"))) {
            for (final var file : files.filter(Files::isRegularFile).toList()) {
                final var source = Files.readString(file);
                assertFalse(source.contains("FunctionalBean"), source);
                assertFalse(source.contains("FunctionalListener"), source);
                assertFalse(source.contains("new demo.A("), source);
            }
        }
        try (final var loader = loader()) {
            @SuppressWarnings("unchecked")
            final var factory = (java.util.function.BiFunction<io.yupiik.fusion.framework.api.configuration.Configuration,
                    java.util.function.Function<String, String>, Object>) factory(loader,
                    "demo.Settings$FusionConfigurationFactory", java.util.function.BiFunction.class);
            assertEquals("Settings[name=standalone]", factory.apply(key -> java.util.Optional.of("standalone"),
                    java.util.function.Function.identity()).toString());
        }
        compile(Map.of("demo.Other", "package demo; public class Other {}"));
        try (final var loader = loader()) {
            assertTrue(module(loader).beans().anyMatch(it -> it.type().getTypeName().equals("demo.A")));
        }
    }


    @Test
    void defaultPackageModuleRegistersDefaultNestedAndNamedPackageBindings() throws Exception {
        compile(Map.of(
                "Plain", "@io.yupiik.fusion.framework.build.api.scanning.Bean public class Plain {}",
                "Nested", "public class Nested { @io.yupiik.fusion.framework.build.api.scanning.Bean public static class Inner {} }",
                "demo.Named", bean("Named")), "-Afusion.moduleFqn=Module");
        try (final var loader = loader()) {
            final var generated = (FusionModule) loader.loadClass("Module").getConstructor().newInstance();
            assertEquals(List.of("Nested$Inner", "Plain", "demo.Named"), generated.beans().map(it -> it.type().getTypeName()).sorted().toList());
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(generated).start();
                 final var plain = container.lookup(loader.loadClass("Plain"));
                 final var nested = container.lookup(loader.loadClass("Nested$Inner"))) {
                assertEquals("Plain", plain.instance().getClass().getName());
                assertEquals("Nested$Inner", nested.instance().getClass().getName());
            }
        }
    }

    @Test
    void moduleStreamsSurviveIncrementalReorderingAndDefaultPackage() throws Exception {
        compile(Map.of("Z", "@io.yupiik.fusion.framework.build.api.scanning.Bean public class Z {}"), "-Afusion.moduleFqn=Module");
        try (final var loader = loader()) {
            assertEquals(List.of("Z"), ((FusionModule) loader.loadClass("Module").getConstructor().newInstance()).beans().map(it -> it.type().getTypeName()).toList());
        }
        compile(Map.of("A", "@io.yupiik.fusion.framework.build.api.scanning.Bean public class A {}"), "-Afusion.moduleFqn=Module");
        try (final var loader = loader()) {
            assertEquals(List.of("A", "Z"), ((FusionModule) loader.loadClass("Module").getConstructor().newInstance()).beans().map(it -> it.type().getTypeName()).toList());
        }
    }

    @Test
    void publicFactoriesAreCallableWithTheirDeclaredTypes() throws Exception {
        compile(Map.of("demo.A", bean("A"), "demo.Settings", """
                package demo;
                @io.yupiik.fusion.framework.build.api.configuration.RootConfiguration("settings")
                public record Settings(String name) {}
                """, "consumer.Use", """
                package consumer;
                public class Use {
                    public static java.util.function.BiFunction<io.yupiik.fusion.framework.api.configuration.Configuration,
                            java.util.function.Function<String, String>, demo.Settings> settings() {
                        return demo.Module.Settings$FusionConfigurationFactory();
                    }
                }
                """));
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(source.contains("public static java.util.function.BiFunction<"), source);
        assertTrue(source.contains("new demo.Settings("), source);
        assertFalse(source.contains("FusionFactories"), source);
        assertFalse(source.contains("create(String"), source);
        assertFalse(source.contains("entries()"), source);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"explicit", "default", "disabled"})
    void jarContainsDiscoveryButNoIncrementalStateOrSourcePaths(final String mode) throws Exception {
        compile(Map.of("demo.A", bean("A")), switch (mode) {
            case "default" -> "-Afusion.workdir=";
            case "disabled" -> "-Afusion.workdir=false";
            default -> "-Afusion.workdir=" + work.resolve("state");
        });
        final var jar = work.resolve("application.jar");
        try (final var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar));
             final var files = Files.walk(work.resolve("classes"))) {
            for (final var file : files.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new java.util.jar.JarEntry(work.resolve("classes").relativize(file).toString().replace('\\', '/')));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
        try (final var archive = new java.util.jar.JarFile(jar.toFile())) {
            assertTrue(archive.getEntry("META-INF/services/" + FusionModule.class.getName()) != null);
            assertEquals(null, archive.getEntry("META-INF/services/io.yupiik.fusion.framework.api.spi.dispatch.BindingProvider"));
            for (final var entry : archive.stream().toList()) {
                assertFalse(entry.getName().contains("compact-origins"), entry.getName());
                assertFalse(entry.getName().endsWith(".bin"), entry.getName());
                assertFalse(entry.getName().startsWith("io/yupiik/fusion/generated/compact_"), entry.getName());
                try (final var in = archive.getInputStream(entry)) {
                    final var content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                    assertFalse(content.contains(work.toString()), entry.getName());
                    assertFalse(content.contains(work.toUri().toString()), entry.getName());
                }
            }
        }
        assertTrue(Files.exists(work.resolve(".fusion-classes/compact-origins-v1")));
    }

    @Test
    void disabledCacheStillProtectsUnseenOrigins() throws Exception {
        compile(Map.of("demo.A", bean("A"), "demo.B", bean("B")), "-Afusion.workdir=false");
        assertFalse(Files.exists(work.resolve("state/compact-v1.bin")));
        final var failure = assertThrows(AssertionError.class, () -> compile(Map.of("demo.B", bean("B")), "-Afusion.workdir=false"));
        assertTrue(failure.getMessage().contains("full clean rebuild"), failure.getMessage());
        compile(Map.of("demo.A", bean("A"), "demo.B", bean("B")), "-Afusion.workdir=false");
        assertEquals(2, beanCount());
    }

    @Test
    void defaultWorkdirIsExternalAndPersistent() throws Exception {
        work = work.resolve("application/target");
        compile(Map.of("demo.A", bean("A"), "demo.B", bean("B")), "-Afusion.workdir=");
        assertTrue(Files.exists(work.resolve("fusion/compact-v1.bin")));
        assertFalse(Files.exists(work.resolve("classes/META-INF/fusion/compact-origins-v1")));
        compile(Map.of("demo.B", bean("B")), "-Afusion.workdir=");
        assertEquals(2, beanCount());
        Files.delete(work.resolve("fusion/compact-v1.bin"));
        assertThrows(AssertionError.class, () -> compile(Map.of("demo.B", bean("B")), "-Afusion.workdir="));
    }

    @Test
    void holderNamespaceSeparatesModulesAndSourceSetsWithoutAbsolutePaths() throws Exception {
        final var root = work;
        work = root.resolve("checkout-one/project-a/target");
        compile(Map.of("demo.A", beanWithDependency("A")), "-Afusion.moduleFqn=modules.Main");
        final var first = holderNames();
        assertEquals(1, first.size());
        work = root.resolve("checkout-two/project-a/target");
        compile(Map.of("demo.A", beanWithDependency("A")), "-Afusion.moduleFqn=modules.Main");
        assertEquals(first, holderNames());
        compile(Map.of("demo.B", beanWithDependency("B")), "-d", work.resolve("test-classes").toString(),
                "-Afusion.moduleFqn=modules.Tests");
        final var tests = Files.readAllLines(work.resolve(".fusion-test-classes/compact-holders-v1")).stream()
                .filter(it -> it.substring(it.lastIndexOf('.') + 1).startsWith("FusionBindings_")).toList();
        assertEquals(1, tests.size());
        assertNotEquals(first, tests);
        work = root.resolve("checkout-two/project-b/target");
        compile(Map.of("demo.C", beanWithDependency("C")), "-Afusion.moduleFqn=modules.Other");
        assertEquals(1, holderNames().size());
        assertNotEquals(first, holderNames());
        try (final var loader = new URLClassLoader(new URL[]{
                root.resolve("checkout-one/project-a/target/classes").toUri().toURL(),
                root.resolve("checkout-two/project-a/target/test-classes").toUri().toURL(),
                work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            for (final var entry : Map.of("Main", "demo.A", "Tests", "demo.B", "Other", "demo.C").entrySet()) {
                final var module = (FusionModule) loader.loadClass("modules." + entry.getKey()).getConstructor().newInstance();
                assertEquals(List.of(entry.getValue()), module.beans().map(it -> it.type().getTypeName()).toList());
            }
        }
    }

    @Test
    void laterRoundAnnotatedSourcesAreIncluded() throws Exception {
        compileWithProcessors(Map.of("demo.A", bean("A")), List.of(new FusionProcessor(), new LaterSourceProcessor()));
        assertEquals(2, beanCount());
        try (final var generated = Files.walk(work.resolve("generated"))) {
            assertTrue(generated.noneMatch(path -> path.getFileName().toString().equals("package-info.java") ||
                    path.getFileName().toString().startsWith("compact_")));
        }
        try (final var loader = loader()) {
            assertEquals(List.of("demo.A", "demo.Later"), module(loader).beans()
                    .map(it -> it.type().getTypeName()).toList());
        }
    }

    @Test
    void standaloneGenerationRemovesRoundDirectories() throws Exception {
        compile(Map.of("demo.A", bean("A")), "-Afusion.generateModule=false");
        try (final var generated = Files.walk(work.resolve("generated"))) {
            assertTrue(generated.noneMatch(path -> path.getFileName().toString().startsWith("compact_")));
        }
    }

    private static class LaterSourceProcessor extends AbstractProcessor {
        private boolean generated;

        @Override
        public Set<String> getSupportedAnnotationTypes() {
            return Set.of("*");
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.RELEASE_17;
        }

        @Override
        public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment round) {
            if (!generated && !round.processingOver()) {
                generated = true;
                try (final var out = processingEnv.getFiler().createSourceFile("demo.Later").openWriter()) {
                    out.write("package demo; @io.yupiik.fusion.framework.build.api.scanning.Bean public class Later {}");
                } catch (final java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            return false;
        }
    }

    @Test
    void losingAllExternalStateStillRequiresCleaningExistingClassOutput() throws Exception {
        compile(Map.of("demo.A", bean("A"), "demo.B", bean("B")));
        assertTrue(holderNames().isEmpty(), "Inline-only modules must retain the lost-cache safety check");
        Files.delete(work.resolve("state/compact-v1.bin"));
        Files.delete(work.resolve(".fusion-classes/compact-origins-v1"));
        final var failure = assertThrows(AssertionError.class, () -> compile(Map.of("demo.B", bean("B"))));
        assertTrue(failure.getMessage().contains("full clean rebuild"), failure.getMessage());
    }

    @Test
    void metadataAliasesWorkWithoutIndexesAndSourceAnnotationRemovalUpdatesMetadata() throws Exception {
        final var one = "package demo; @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name=\"kind\", value=\"one\") public @interface One {}";
        final var two = "package demo; @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name=\"kind\", value=\"two\") public @interface Two {}";
        final var a = bean("A").replace("public class", "@One public class");
        final var b = bean("B").replace("public class", "@Two public class");
        compile(Map.of("demo.One", one, "demo.Two", two, "demo.A", a, "demo.B", b));
        assertEquals(Map.of("demo.A", "one", "demo.B", "two"), aliasMetadata());
        assertNoAliasState();
        compile(Map.of("demo.One", "package demo; public @interface One {}"));
        compile(Map.of("demo.A", a, "demo.B", b));
        assertEquals(Map.of("demo.B", "two"), aliasMetadata());
        assertNoAliasState();
    }

    private Map<String, String> aliasMetadata() throws Exception {
        final var result = new java.util.TreeMap<String, String>();
        try (final var loader = loader()) {
            module(loader).beans().forEach(bean -> {
                if (bean.data().get("kind") instanceof String value) {
                    result.put(bean.type().getTypeName(), value);
                }
            });
        }
        return result;
    }

    private void assertNoAliasState() throws Exception {
        try (final var files = Files.walk(work)) {
            assertEquals(List.of(), files.filter(Files::isRegularFile)
                    .filter(it -> it.getFileName().toString().contains("aliases-v1") ||
                            it.getFileName().toString().contains("metadata-aliases") ||
                            it.getFileName().toString().endsWith("$MetadataContributor.class"))
                    .toList());
        }
        assertFalse(Files.exists(work.resolve("classes/META-INF/services/io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void largeStandaloneConfigurationIsNotRejectedBySourceBatchingHeuristic() throws Exception {
        final var fields = IntStream.range(0, 220).mapToObj(it -> "String property" + it).collect(joining(", "));
        compile(Map.of("demo.Large", "package demo; @io.yupiik.fusion.framework.build.api.configuration.RootConfiguration(\"large\") public record Large(" + fields + ") {}"),
                "-Afusion.generateModule=false", "-Afusion.generateBeanForRootConfiguration=false");
        final var source = Files.readString(holderSources().get(0));
        assertTrue(source.length() > 48_000, "Fixture must exercise an artifact above the batching heuristic: " + source.length());
        try (final var loader = loader()) {
            final var factory = (java.util.function.BiFunction<io.yupiik.fusion.framework.api.configuration.Configuration, java.util.function.Function<String, String>, Object>)
                    factory(loader, "demo.Large$FusionConfigurationFactory", java.util.function.BiFunction.class);
            final var value = factory.apply(key -> java.util.Optional.of("value"), java.util.function.Function.identity());
            assertEquals("demo.Large", value.getClass().getName());
            assertTrue(value.toString().contains("property219=value"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void numericConfigurationLiteralDefaultsCompileWithoutRedundantCastWarnings() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("settings")
                public record Settings(@Property(defaultValue="8081") int port,
                    @Property(defaultValue="2L") long big,
                    @Property(defaultValue="3.5f") float ratio,
                    @Property(defaultValue="4.5") double precise,
                    @Property(defaultValue="5") long widened) {}
                """));
        try (final var loader = loader()) {
            final var factory = (java.util.function.BiFunction<io.yupiik.fusion.framework.api.configuration.Configuration,
                    java.util.function.Function<String, String>, Object>) factory(
                    loader, "demo.Settings$FusionConfigurationFactory", java.util.function.BiFunction.class);
            assertEquals("Settings[port=8081, big=2, ratio=3.5, precise=4.5, widened=5]",
                    factory.apply(key -> java.util.Optional.empty(), java.util.function.Function.identity()).toString());
        }
    }

    private List<Path> holderSources() throws Exception {
        try (final var files = Files.walk(work.resolve("generated"))) {
            return files.filter(it -> it.getFileName().toString().startsWith("FusionBindings_")).sorted().toList();
        }
    }

    private List<String> holderNames() throws Exception {
        return Files.readAllLines(work.resolve(".fusion-classes/compact-holders-v1")).stream()
                .filter(it -> it.substring(it.lastIndexOf('.') + 1).startsWith("FusionBindings_")).toList();
    }

    private <T> T factory(final ClassLoader loader, final String id, final Class<T> type) throws Exception {
        final int separator = id.lastIndexOf('.');
        return GeneratedFactories.invoke(loader, id.substring(0, separator), id.substring(separator + 1), type);
    }

    private String constructionSources() throws Exception {
        final var source = new StringBuilder();
        try (final var files = Files.walk(work.resolve("generated"))) {
            for (final var file : files.filter(it -> it.toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(file));
            }
        }
        return source.toString();
    }

    @Test
    void jsonModelIndexDoesNotDependOnSchemas() throws Exception {
        compile(Map.of("demo.Model", """
                package demo;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Model(String value) {}
                """), "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        assertEquals(List.of("demo.Model"), jsonModelNames());
        compile(Map.of("demo.A", bean("A")), "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        assertEquals(List.of("demo.Model"), jsonModelNames());
        compile(Map.of("demo.Model", "package demo; public record Model(String value) {}"), "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        assertEquals(List.of(), jsonModelNames());
    }

    @Test
    void deletedJsonSourcesPruneExternalState() throws Exception {
        compile(Map.of("demo.Model", "package demo; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Model(String value) {}"),
                "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        Files.delete(work.resolve("sources/demo/Model.java"));
        compile(Map.of("demo.A", bean("A")), "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        assertEquals(List.of(), jsonModelNames());
    }

    @Test
    void nestedModelIndexNamesRemainDiscoverableWithoutSchemas() throws Exception {
        compile(Map.of("demo.Models", """
                package demo;
                public class Models {
                    @io.yupiik.fusion.framework.build.api.json.JsonModel
                    public record Inner(String value) {}
                }
                """), "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        compile(Map.of("demo.Consumer", """
                package demo;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Consumer(Models.Inner value) {}
                """), "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        assertEquals(List.of("demo.Consumer", "demo.Models$Inner"), jsonModelNames());
    }

    private List<String> jsonModelNames() throws Exception {
        assertFalse(Files.exists(work.resolve("classes/META-INF/fusion/json/models-v1")));
        final var properties = new java.util.Properties();
        try (final var in = Files.newBufferedReader(work.resolve("state/json-models-v1.properties"))) {
            properties.load(in);
        }
        return properties.stringPropertyNames().stream().sorted().toList();
    }

    private String bean(final String name) {
        return "package demo; @io.yupiik.fusion.framework.build.api.scanning.Bean public class " + name + " {}";
    }
    private String beanWithDependency(final String name) {
        return bean(name).replace("{}", "{ public " + name +
                "(io.yupiik.fusion.framework.api.configuration.Configuration configuration) {" +
                " java.util.Objects.requireNonNull(configuration); } }");
    }


    private long beanCount() throws Exception {
        try (final var loader = loader()) {
            return module(loader).beans().count();
        }
    }

    private FusionModule module(final ClassLoader loader) throws Exception {
        return (FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
    }

    private URLClassLoader loader() throws Exception {
        return new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader());
    }

    private void assertTypedCallbacks(final String type) {
        final var output = new StringWriter();
        final var tool = java.util.spi.ToolProvider.findFirst("javap").orElseThrow();
        assertEquals(0, tool.run(new PrintWriter(output), new PrintWriter(output), "-v", "-p", "-classpath",
                work.resolve("classes") + File.pathSeparator + System.getProperty("java.class.path"), type), output.toString());
        assertTrue(output.toString().contains("LambdaMetafactory"), output.toString());
        assertFalse(output.toString().contains("Object invoke("), output.toString());
        assertFalse(output.toString().contains("io/yupiik/fusion/framework/api/spi/dispatch/Dispatch.invoke"), output.toString());
        assertFalse(output.toString().contains("java/lang/reflect/Method"), output.toString());
    }

    @Test
    void obsoleteShardsAndChangedModuleNamespacesAreRemoved() throws Exception {
        final var many = IntStream.range(0, 40).mapToObj(i ->
                "@io.yupiik.fusion.framework.build.api.scanning.Bean public static class B" + i +
                        " { public B" + i + "(io.yupiik.fusion.framework.api.configuration.Configuration configuration) {" +
                        " java.util.Objects.requireNonNull(configuration); } }").collect(joining("\n"));
        compile(Map.of("demo.Many", "package demo; public class Many {" + many + "}"), "-Afusion.moduleFqn=other.Module");
        final List<Path> oldHolders;
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            oldHolders = files.filter(it -> it.getFileName().toString().startsWith("FusionBindings_")).toList();
        }
        assertEquals(2, oldHolders.size());
        Files.delete(oldHolders.get(0)); // source-output can be cleaned independently of classes
        compile(Map.of("demo.Many", """
                package demo;
                public class Many {
                    @io.yupiik.fusion.framework.build.api.scanning.Bean public static class B0 {
                        public B0(io.yupiik.fusion.framework.api.configuration.Configuration configuration) {
                            java.util.Objects.requireNonNull(configuration);
                        }
                    }
                }
                """), "-Afusion.moduleFqn=other.Module");
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            assertEquals(1, files.filter(it -> it.getFileName().toString().startsWith("FusionBindings_")).count());
        }
        compile(Map.of("demo.Many", "package demo; public class Many {}", "demo.A", bean("A")),
                "-Afusion.moduleFqn=demo.Replacement");
        for (final var holder : oldHolders) {
            assertFalse(Files.exists(holder));
            assertFalse(Files.exists(work.resolve("classes/demo/" + holder.getFileName().toString().replace(".java", ".class"))));
        }
        assertFalse(Files.exists(work.resolve("classes/other/Module.class")));
        assertFalse(Files.exists(work.resolve("generated/other/Module.java")));
        final var index = work.resolve("classes/META-INF/services/" + FusionModule.class.getName());
        assertEquals("demo.Replacement", Files.readString(index).strip());
        compile(Map.of("demo.A", bean("A")), "-Afusion.generateModule=false");
        assertTrue(Files.readString(index).isBlank());
        assertFalse(Files.exists(work.resolve("classes/demo/Replacement.class")));
    }

    @Test
    void missingModuleInventoryRequiresCleaningInsteadOfKeepingUnknownGeneratedModules() throws Exception {
        compile(Map.of("demo.A", bean("A")));
        Files.delete(work.resolve(".fusion-classes/generated-module"));
        final var failure = assertThrows(AssertionError.class, () -> compile(Map.of("demo.A", bean("A")),
                "-Afusion.moduleFqn=demo.Replacement"));
        assertTrue(failure.getMessage().contains("Missing generated module build state"), failure.getMessage());
    }

    @Test
    void missingOriginClassPrunesOnlyItsCachedDescriptor() throws Exception {
        compile(Map.of("demo.A", bean("A"), "demo.B", bean("B")));
        Files.delete(work.resolve("classes/demo/A.class"));
        compile(Map.of("demo.B", bean("B")));
        try (final var loader = new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var module = (FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
            assertEquals(List.of("demo.B"), module.beans().map(it -> it.type().getTypeName()).toList());
        }
    }

    @Test
    void failedGeneratedSourcesCanBeRecompiledWithoutLosingUnseenOrigins() throws Exception {
        compile(Map.of("demo.A", bean("A"), "demo.B", bean("B")));
        assertThrows(AssertionError.class, () -> compile(Map.of("demo.B", """
                package demo;
                @io.yupiik.fusion.framework.build.api.scanning.Bean public abstract class B {}
                """)));
        compile(Map.of("demo.B", bean("B")));
        try (final var loader = new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var module = (FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
            assertEquals(List.of("demo.A", "demo.B"),
                    module.beans().map(it -> it.type().getTypeName()).toList());
        }
    }

    @Test
    void unseenClasspathAliasesAndBeanDescriptorsAreRetainedWithoutExternalAliasCache() throws Exception {
        compile(Map.of("demo.Alias", """
                package demo;
                @io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias(name="kind", value="classpath")
                public @interface Alias {}
                """, "demo.A", bean("A").replace("public class", "@Alias public class")));
        assertEquals(Map.of("demo.A", "classpath"), aliasMetadata());
        assertNoAliasState();
        compile(Map.of("demo.B", bean("B").replace("public class", "@Alias public class")));
        assertEquals(Map.of("demo.A", "classpath", "demo.B", "classpath"), aliasMetadata());
        assertNoAliasState();
    }

    @Test
    void separateClasspathModelsAreResolvedWithoutSchemasOrProcessorLoaderVisibility() throws Exception {
        final var jars = new ArrayList<Path>();
        for (final var name : List.of("One", "Two")) {
            final var classes = work.resolve("dependency-" + name + "/classes");
            Files.createDirectories(classes);
            compile(Map.of("dependency." + name, "package dependency; @io.yupiik.fusion.framework.build.api.json.JsonModel public record " + name + "(String value) {" +
                            " @io.yupiik.fusion.framework.build.api.json.JsonModel public enum Mode { ONE; public String toJsonString() { return \"custom\"; } } }",
                            "dependency." + name + "$Literal", "package dependency; @io.yupiik.fusion.framework.build.api.json.JsonModel public record " + name + "$Literal(String value) {}"),
                    "-d", classes.toString(), "-s", work.resolve("dependency-" + name + "/generated").toString(),
                    "-Afusion.workdir=" + work.resolve("dependency-" + name + "/state"),
                    "-Afusion.generateModule=false", "-Afusion.generateBeanForJsonCodec=false",
                    "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
            assertFalse(Files.exists(classes.resolve("META-INF/fusion/json/schemas.json")));
            assertFalse(Files.exists(classes.resolve("META-INF/fusion/json/models-v1")));
            final var jar = work.resolve(name + ".jar");
            try (final var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar));
                 final var files = Files.walk(classes)) {
                for (final var file : files.filter(Files::isRegularFile).toList()) {
                    out.putNextEntry(new java.util.jar.JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }
            try (final var archive = new java.util.jar.JarFile(jar.toFile())) {
                assertEquals(null, archive.getEntry("META-INF/fusion/json/models-v1"));
                assertTrue(archive.stream().noneMatch(it -> it.getName().endsWith(".properties")), "No incremental state in JAR");
            }
            jars.add(jar);
        }
        compile(Map.of("demo.Consumer", """
                package demo;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Consumer(dependency.One one, dependency.Two two,
                    dependency.One.Mode mode, dependency.Two.Mode secondMode,
                    dependency.One$Literal literal, dependency.Two$Literal secondLiteral) {}
                """), "-classpath", jars.get(0) + File.pathSeparator + jars.get(1) + File.pathSeparator + System.getProperty("java.class.path"),
                "-processorpath", System.getProperty("java.class.path"),
                "-Afusion.generateModule=false", "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        assertEquals(List.of("demo.Consumer"), jsonModelNames());
        assertThrows(AssertionError.class, () -> compile(Map.of(
                "dependency.Two", "package dependency; public record Two(String value) {}",
                "demo.Shadow", "package demo; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Shadow(dependency.Two two) {}"),
                "-classpath", jars.get(0) + File.pathSeparator + jars.get(1) + File.pathSeparator + System.getProperty("java.class.path"),
                "-Afusion.generateModule=false", "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false"));
    }

    @Test
    void legacyPublishedCodecIsDiscoveredOnTheCompilationClasspath() throws Exception {
        final var dependency = work.resolve("legacy/classes");
        Files.createDirectories(dependency);
        compileWithProcessors(Map.of("legacy.Model", "package legacy; public record Model(String value) {}",
                "legacy.Model$FusionJsonCodec", """
                        package legacy;
                        public abstract class Model$FusionJsonCodec implements io.yupiik.fusion.json.serialization.JsonCodec<Model> {}
                        """), List.of(), "-proc:none", "-d", dependency.toString());
        compile(Map.of("demo.Consumer", """
                package demo;
                @io.yupiik.fusion.framework.build.api.json.JsonModel
                public record Consumer(legacy.Model model) {}
                """), "-classpath", dependency + File.pathSeparator + System.getProperty("java.class.path"),
                "-processorpath", System.getProperty("java.class.path"),
                "-Afusion.generateModule=false", "-Afusion.generateJsonSchemas=false", "-Afusion.generatePartialOpenRPC=false");
        assertEquals(List.of("demo.Consumer"), jsonModelNames());
    }

    @Test
    void removedJsonAnnotationIsNotRediscoveredFromOwnStaleClassOutput() throws Exception {
        compile(Map.of("demo.Model", "package demo; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Model(String value) {}"));
        assertThrows(AssertionError.class, () -> compile(Map.of(
                "demo.Model", "package demo; public record Model(String value) {}",
                "demo.Consumer", "package demo; @io.yupiik.fusion.framework.build.api.json.JsonModel public record Consumer(Model model) {}")));
    }

    @Test
    void templateSubstitutionPreservesMetadataLiterals() throws Exception {
        final var literal = "$id $dispatch $method $method_property0 $factory{missing} lookup(container, ";
        compile(Map.of("demo.A", "package demo; @io.yupiik.fusion.framework.build.api.scanning.Bean " +
                "@io.yupiik.fusion.framework.build.api.metadata.BeanMetadata(name=\"literal\", value=\"" + literal + "\") public class A {}"));
        try (final var loader = new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var module = (FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
            final var bean = module.beans().findFirst().orElseThrow();
            assertEquals(loader.loadClass("demo.A"), bean.type());
            assertEquals(literal, bean.data().get("literal"));
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module).start();
                 final var instance = container.lookup(bean.type())) {
                assertEquals("demo.A", instance.instance().getClass().getName());
            }
        }
    }

    private void compile(final Map<String, String> sources, final String... extra) throws Exception {
        compileWithProcessors(sources, List.of(new FusionProcessor()), extra);
    }

    private void compileWithProcessors(final Map<String, String> sources, final List<Processor> processors, final String... extra) throws Exception {
        final var paths = new ArrayList<Path>();
        for (final var entry : sources.entrySet()) {
            final var path = work.resolve("sources/" + entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue());
            paths.add(path);
        }
        Files.createDirectories(work.resolve("classes"));
        Files.createDirectories(work.resolve("generated"));
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var output = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var options = new ArrayList<>(List.of("--release", "17", "-classpath",
                    work.resolve("classes") + File.pathSeparator + System.getProperty("java.class.path"),
                    "-d", work.resolve("classes").toString(), "-s", work.resolve("generated").toString(),
                    "-Afusion.workdir=" + work.resolve("state"), "-Afusion.moduleFqn=demo.Module", "-Xlint:all,-processing,-serial", "-Werror"));
            options.addAll(List.of(extra));
            final var task = compiler.getTask(output, manager, null, options, null, manager.getJavaFileObjectsFromPaths(paths));
            task.setProcessors(processors);
            assertTrue(task.call(), output.toString());
        }
    }
}
