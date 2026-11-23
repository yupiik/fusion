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

import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
import io.yupiik.fusion.framework.api.container.listener.FunctionalListener;
import io.yupiik.fusion.framework.processor.test.ProcessorInternals;
import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.io.DataInputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingEmitterTest {
    @TempDir
    Path work;

    @Test
    void moduleOwnsBeanExpressionsAndBusinessConstructionStaysLazy() throws Exception {
        compileFragments(List.of(new MethodSource(0,
                        "private static demo.Input.Settings $method(io.yupiik.fusion.framework.api.RuntimeContainer container, java.util.List<io.yupiik.fusion.framework.api.Instance<?>> dependencies) { return new demo.Input.Settings(\"lazy\"); }",
                        "(container, dependencies) -> new demo.Input.Settings(\"lazy\")")),
                beanExpression("java.util.Map.of()", "$callback{0}"), 2, "classes",
                "io.yupiik.fusion.framework.api.container.FusionBean<?>", "bean");
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(source.contains("builder.add(new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<>"), source);
        assertFalse(source.contains("$definition"), source);
        assertFalse(source.contains(".copy()"), source);
        assertFalse(Files.exists(work.resolve("generated/demo/FusionFactories.java")));
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            assertEquals(List.of("Module.java"), files.map(it -> it.getFileName().toString()).sorted().toList());
        }
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final FunctionalBean<?> first = descriptor(loader, 0, FunctionalBean.class);
            final FunctionalBean<?> second = descriptor(loader, 0, FunctionalBean.class);
            final var events = loader.loadClass("demo.Input").getField("events");
            assertEquals(0, events.getInt(null));
            assertNotSame(first, second);
            assertNotEquals(first, second);
            assertNotSame(first.getLock(), second.getLock());
            assertEquals("lazy", first.create(null, new ArrayList<>()).toString());
            assertEquals(1, events.getInt(null));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void moduleOwnsListenersAndTheirPrivateTypedHelpers() throws Exception {
        compileFragments(List.of(new MethodSource(0, """
                        private static void $method(io.yupiik.fusion.framework.api.RuntimeContainer container, String event) {
                            demo.Input.events++;
                        }
                        private static String $method_detail() { return "private"; }
                        """)),
                "new io.yupiik.fusion.framework.api.container.listener.FunctionalListener<String>(String.class, 19, $callback{0})",
                2, "classes", "io.yupiik.fusion.framework.api.container.FusionListener<?>", "listener");
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(source.contains("builder.add(new io.yupiik.fusion.framework.api.container.listener.FunctionalListener<String>"), source);
        assertFalse(source.contains(".copy()"), source);
        assertFalse(Files.exists(work.resolve("generated/demo/FusionFactories.java")));
        assertTrue(source.contains("private static void binding0_0("), source);
        assertTrue(source.contains("private static String binding0_0_detail()"), source);
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var first = (FunctionalListener<String>) descriptor(loader, 0, FunctionalListener.class);
            final var second = (FunctionalListener<String>) descriptor(loader, 0, FunctionalListener.class);
            assertNotSame(first, second);
            assertNotEquals(first, second);
            final var events = loader.loadClass("demo.Input").getField("events");
            assertEquals(0, events.getInt(null));
            first.onEvent(null, "first");
            second.onEvent(null, "second");
            assertEquals(2, events.getInt(null));
        }
    }

    @Test
    void largeDescriptorExpressionStaysInItsStreamOwnerWithoutAFactory() throws Exception {
        compileFragments(List.of(), beanExpression("java.util.Map.of(\"large\", \"" + "x".repeat(8_001) + "\")",
                        "(container, dependencies) -> new demo.Input.Settings(\"overflow\")"),
                1, "classes", "io.yupiik.fusion.framework.api.container.FusionBean<?>", "bean");
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(source.contains("builder.add(new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<>"), source);
        assertFalse(source.contains("$definition"), source);
        assertFalse(source.contains(".copy()"), source);
        assertFalse(Files.exists(work.resolve("generated/demo/FusionFactories.java")));
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final FunctionalBean<?> first = descriptor(loader, 0, FunctionalBean.class);
            final FunctionalBean<?> second = descriptor(loader, 0, FunctionalBean.class);
            assertEquals(0, loader.loadClass("demo.Input").getField("events").getInt(null));
            assertNotSame(first, second);
            assertNotSame(first.getLock(), second.getLock());
            assertEquals("overflow", first.create(null, new ArrayList<>()).toString());
        }
    }

    @Test
    void aggregateDescriptorExpressionsOverflowIntoBoundedStreamOwners() throws Exception {
        compileFragments(List.of(), beanExpression("java.util.Map.of(\"large\", \"" + "x".repeat(1_500) + "\")",
                        "(container, dependencies) -> new demo.Input.Settings(\"bounded\")"),
                40, "classes", "io.yupiik.fusion.framework.api.container.FusionBean<?>", "bean");
        final var source = Files.readString(work.resolve("generated/demo/Module.java"));
        assertTrue(source.contains(".beans().forEachOrdered(builder::add)"), source);
        assertFalse(source.contains(".copy()"), source);
        assertFalse(Files.exists(work.resolve("generated/demo/FusionFactories.java")));
        final var holder = holderSource();
        assertTrue(holder.contains("builder.add(new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<>"), holder);
        assertFalse(holder.contains(" factory"), holder);
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            for (final var file : files.filter(it -> it.toString().endsWith(".java")).toList()) {
                final var content = Files.readString(file);
                assertFalse(content.contains("$definition"), content);
                assertFalse(content.contains(".copy()"), content);
                assertFalse(content.contains("public static io.yupiik.fusion.framework.api.container.FusionBean<?>"), content);
                final var codes = codeLengths(work.resolve("classes/demo/" + file.getFileName().toString().replace(".java", ".class")));
                assertTrue(codes.values().stream().allMatch(size -> size < 32_000), codes.toString());
            }
        }
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var module = (io.yupiik.fusion.framework.api.container.FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
            final var first = module.beans().toList();
            final var second = module.beans().toList();
            assertEquals(40, first.size());
            for (int index = 0; index < 40; index++) {
                assertNotSame(first.get(index), second.get(index));
                assertNotSame(((FunctionalBean<?>) first.get(index)).getLock(), ((FunctionalBean<?>) second.get(index)).getLock());
            }
            assertEquals(0, loader.loadClass("demo.Input").getField("events").getInt(null));
        }
    }

    @Test
    void standaloneObjectFactoryRemainsFresh() throws Exception {
        compileFragments(List.of(), "new Object()", 1, "classes", "Object");
        final var source = constructionSource();
        assertTrue(source.contains("public static Object A() { return new Object(); }"), source);
        assertFalse(source.contains("$definition"), source);
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            assertNotSame(factory(loader, "demo.A", Object.class), factory(loader, "demo.A", Object.class));
        }
    }

    @Test
    void standaloneTypedFactoryKeepsLazyBusinessConstruction() throws Exception {
        compileFragments(List.of(new MethodSource(0,
                        "private static demo.Input.Settings $method() { return new demo.Input.Settings(\"standalone\"); }")),
                "$callback{0}", 1, "classes", "java.util.function.Supplier<demo.Input.Settings>");
        final var source = constructionSource();
        assertTrue(source.contains("public static java.util.function.Supplier<demo.Input.Settings> A()"), source);
        assertFalse(source.contains("$definition"), source);
        assertFalse(source.contains(".copy()"), source);
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var first = factory(loader, "demo.A", java.util.function.Supplier.class);
            final var second = factory(loader, "demo.A", java.util.function.Supplier.class);
            assertEquals(0, loader.loadClass("demo.Input").getField("events").getInt(null));
            final var value = first.get();
            assertEquals("standalone", value.toString());
            assertNotSame(value, second.get());
            assertEquals(1, loader.loadClass("demo.Input").getField("events").getInt(null));
        }
    }

    private String beanExpression(final String metadata, final String constructor) {
        return "new io.yupiik.fusion.framework.api.container.bean.FunctionalBean<>(demo.Input.Settings.class, " +
                "io.yupiik.fusion.framework.api.scope.DefaultScoped.class, 3, 7, " + metadata + ", " + constructor +
                ", null, null, null, false)";
    }

    @Test
    @SuppressWarnings("unchecked")
    void directCallbacksKeepTypedStaticHelpersWithoutRuntimeDispatch() throws Exception {
        compileFragments(List.of(
                new MethodSource(0, "private static String $method() { return $helper{1}(); }"),
                new MethodSource(1, "private static String $method() { return \"typed\"; }")),
                "(java.util.function.Supplier<String>) $callback{0}", 2);
        final var source = holderSource();
        assertFalse(source.contains("Object invoke("), source);
        assertFalse(source.contains("switch (id)"), source);
        assertFalse(source.contains("switch (operation)"), source);
        assertTrue(source.contains("private static String call0_0()"), source);
        assertTrue(constructionSource().contains("::call0_0"));
        assertTrue(source.contains(".call0_1();"), source);
        assertFalse(source.contains(" factory0()"), source);
        int helpers = 0;
        try (final var files = Files.walk(work.resolve("classes"))) {
            for (final var file : files.filter(it -> it.getFileName().toString().startsWith("FusionBindings_") &&
                    it.toString().endsWith(".class")).toList()) {
                final var codes = codeLengths(file);
                assertFalse(codes.containsKey("invoke"), codes.toString());
                assertFalse(codes.containsKey("factory"), codes.toString());
                helpers += (int) codes.keySet().stream().filter(it -> it.startsWith("call")).count();
            }
        }
        assertEquals(4, helpers);
        assertTrue(new String(Files.readAllBytes(work.resolve("classes").resolve(
                        constructionOwner().replace('.', '/') + ".class")),
                java.nio.charset.StandardCharsets.ISO_8859_1).contains("LambdaMetafactory"),
                "Hidden lambda classes are an accepted callback cost");
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var first = factory(loader, "demo.A", java.util.function.Supplier.class);
            final var second = factory(loader, "demo.B", java.util.function.Supplier.class);
            assertEquals("typed", first.get());
            assertEquals("typed", second.get());
            assertFalse(Files.exists(work.resolve("classes/META-INF/services/io.yupiik.fusion.framework.api.spi.dispatch.BindingProvider")));
            assertFalse(source.contains("create(String"), source);
            assertFalse(source.contains("entries()"), source);
        }
    }

    @Test
    void namespacesHelpersButPreservesLiteralsCommentsAndBusinessDollarIdentifiers() throws Exception {
        final var declarations = """
                private static String $method() {
                    int $business = 1, $methodology = 2, $idValue = 3, $dispatchValue = 4, $factoryValue = 5;
                    char dollar = '$';
                    // $method_property0 $dispatch $id $factory{missing} $holder
                    /* $method_property0 $dispatch $id $factory{missing} $holder */
                    String quoted = "\\\"$method_property0\\\"";
                    String block = \"\"\"
                        " $method_property0 $dispatch $id $factory{missing} $holder
                        \"\"\";
                    return $method_property0() + ":" + ($business + $methodology + $idValue + $dispatchValue + $factoryValue) + dollar + quoted + block.strip();
                }
                private static String $method_property0() { return "$method_property0"; }
                """;
        compile(declarations, "(java.util.function.Supplier<String>) $callback{0}");
        final var holder = holderSource();
        assertTrue(holder.contains("call0_0_property0()"), holder);
        assertTrue(holder.contains("call1_0_property0()"), holder);
        assertTrue(holder.contains("// $method_property0 $dispatch $id $factory{missing} $holder"), holder);
        assertTrue(holder.contains("/* $method_property0 $dispatch $id $factory{missing} $holder */"), holder);
        assertTrue(holder.contains("int $business = 1, $methodology = 2"), holder);
        assertFalse(holder.contains("private final String prefix;"), holder);
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var callback = factory(loader, "demo.A", java.util.function.Supplier.class);
            final var expected = "$method_property0:15$\"$method_property0\"\" $method_property0 $dispatch $id $factory{missing} $holder";
            assertEquals(expected, callback.get());
            assertEquals(expected, factory(loader, "demo.B", java.util.function.Supplier.class).get());
        }
    }

    @Test
    void usesExistingHoldersForDefaultContextAcrossShards() throws Exception {
        compileFragments(List.of(new MethodSource(0, """
                private static String $method() {
                    io.yupiik.fusion.framework.api.configuration.Configuration configuration = key -> java.util.Optional.of(key);
                    java.util.function.Function<String, String> keyMapper = key -> "mapped:" + key;
                    return new $holder(configuration, keyMapper, "nested").$method_default();
                }
                private String $method_default() {
                    return ((java.util.function.Function<String, String>) configuration -> {
                        try {
                            throw new IllegalArgumentException(configuration);
                        } catch (IllegalArgumentException prefix) {
                            Object value = prefix.getMessage();
                            return value instanceof String keyMapper ?
                                this.configuration.get(this.keyMapper.apply(this.prefix)).orElseThrow() + ":" + keyMapper : "missing";
                        }
                    }).apply("shadow");
                }
                """)), "(java.util.function.Supplier<String>) $callback{0}", 40);
        try (final var files = Files.walk(work.resolve("classes"))) {
            final var holders = files.filter(it -> it.getFileName().toString().startsWith("FusionBindings_") &&
                    it.toString().endsWith(".class")).toList();
            assertEquals(2, holders.size(), holders.toString());
            assertTrue(holders.stream().noneMatch(it -> it.getFileName().toString().contains("$")), holders.toString());
        }
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            for (int index = 0; index < 40; index++) {
                final var id = "demo." + (index < 26 ? String.valueOf((char) ('A' + index)) : "Artifact" + index);
                assertEquals("mapped:nested:shadow", factory(loader, id, java.util.function.Supplier.class).get());
            }
        }
        final var holder = holderSource();
        assertTrue(holder.contains("private final String prefix;"), holder);
        assertFalse(holder.contains("$holder"), holder);
        assertFalse(holder.contains("DefaultContext"), holder);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"$factory"})
    void failsBeforeWritingHolderForUnresolvedReservedPlaceholder(final String placeholder) throws Exception {
        final var failure = assertThrows(RuntimeException.class, () -> compile(
                "private static Object $method() { return " + placeholder + "; }", "new Object()"));
        assertTrue(causes(failure).contains("Unresolved reserved compact placeholder: " + placeholder), causes(failure));
        try (final var files = Files.walk(work.resolve("generated"))) {
            assertFalse(files.anyMatch(it -> it.getFileName().toString().startsWith("FusionBindings_")));
        }
    }

    @Test
    void preservesNestedHelperClassSourceWithoutParsingIt() throws Exception {
        compile("""
                private static String $method() { return new $method_Context().value(); }
                private static final class $method_Context {
                    private String value() { return "nested"; }
                }
                """, "(java.util.function.Supplier<String>) $callback{0}");
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            assertEquals("nested", factory(loader, "demo.A", java.util.function.Supplier.class).get());
            assertEquals("nested", factory(loader, "demo.B", java.util.function.Supplier.class).get());
        }
        assertTrue(holderSource().contains("class call0_0_Context"));
        assertTrue(holderSource().contains("class call1_0_Context"));
    }

    @Test
    void rejectsMethodHelperInFactory() throws Exception {
        final var failure = assertThrows(RuntimeException.class, () -> compile(
                "private static Object $method() { return null; }", "$method_property0()"));
        assertTrue(causes(failure).contains("Unresolved reserved compact placeholder: $method_property0"), causes(failure));
    }

    @Test
    void rejectsUnknownFactoryReferencesBeforeJavacSeesThem() throws Exception {
        final var failure = assertThrows(RuntimeException.class, () -> compile(
                "private static Object $method() { return $factory{missing}; }", "new Object()"));
        assertTrue(causes(failure).contains("Unknown compact factory reference: missing"), causes(failure));
    }

    @Test
    void resolvesCrossArtifactReferencesAfterAllFactoriesAreAssigned() throws Exception {
        compile("private static Object $method() { return $factory{demo.B}; }", "\"value\"");
        final var holder = holderSource();
        assertFalse(holder.contains("$factory{"), holder);
        assertTrue(holder.contains("return ((Object) (\"value\"));"), holder);
        assertFalse(holder.contains("FusionFactories"), holder);
    }

    @Test
    void factoryOnlyBindingHasNoImplicitOperations() throws Exception {
        compile(null, "new Object()");
        assertTrue(constructionSource().contains("public static Object A() { return new Object(); }"));
        assertTrue(holderSource().contains("public static Object A() { return new Object(); }"));
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            assertEquals(List.of("A", "B"), java.util.Arrays.stream(loader.loadClass(constructionOwner()).getDeclaredMethods())
                    .map(java.lang.reflect.Method::getName).sorted().toList());
            assertEquals(Object.class, factory(loader, "demo.A", Object.class).getClass());
        }
    }

    @Test
    void explicitTrivialCallbacksStayLazyAndDoNotGenerateForwarders() throws Exception {
        compileFragments(List.of(
                new MethodSource(0, "private static demo.Input.Settings $method() { return new demo.Input.Settings(\"lazy\"); }",
                        "() -> new demo.Input.Settings(\"lazy\")"),
                new MethodSource(1, "private static void $method() {}", "() -> {}")),
                "java.util.List.of((java.util.function.Supplier<demo.Input.Settings>) $callback{0}, (Runnable) $callback{1})", 2);
        final var source = constructionSource();
        assertTrue(source.contains("() -> new demo.Input.Settings(\"lazy\")"), source);
        assertTrue(source.contains("() -> {}"), source);
        assertFalse(source.contains("::call"), source);
        assertFalse(source.contains("factory0"), source);
        assertFalse(source.contains("private static"), source);
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var callbacks = factory(loader, "demo.A", List.class);
            final var events = loader.loadClass("demo.Input").getField("events");
            assertEquals(0, events.getInt(null), "Creating a factory must not execute its constructor callback");
            ((Runnable) callbacks.get(1)).run();
            assertEquals(0, events.getInt(null));
            assertEquals("lazy", ((java.util.function.Supplier<?>) callbacks.get(0)).get().toString());
            assertEquals(1, events.getInt(null));
        }
    }

    @Test
    void substantialInlineCallbackFallsBackToTypedHelper() throws Exception {
        compileFragments(List.of(new MethodSource(0, "private static String $method() { return \"bounded\"; }",
                        "() -> \"" + "x".repeat(8_001) + "\"")),
                "(java.util.function.Supplier<String>) $callback{0}", 1);
        assertTrue(constructionSource().contains("::call0_0"));
        assertFalse(constructionSource().contains("x".repeat(100)));
        assertFalse(holderSource().contains(" factory0()"));
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            assertEquals("bounded", factory(loader, "demo.A", java.util.function.Supplier.class).get());
        }
    }

    @Test
    void retainedHelpersUseReferencesInsteadOfShadowingFactoryLambdas() throws Exception {
        compileFragments(List.of(
                new MethodSource(0, "private static java.util.function.IntUnaryOperator $method(int value) { return $callback{1}; }"),
                new MethodSource(1, "private static int $method(int value) { return $helper{2}(value); }", "value -> value"),
                new MethodSource(2, "private static int $method(int value) { return value; }", "value -> value")),
                "java.util.List.of((java.util.function.IntFunction<java.util.function.IntUnaryOperator>) $callback{0}, " +
                        "(java.util.function.IntUnaryOperator) $callback{1}, (java.util.function.IntUnaryOperator) $callback{2})", 1);
        final var holder = holderSource();
        assertTrue(holder.contains("::call0_1;"), holder);
        assertTrue(holder.contains("private static int call0_1(int value)"), holder);
        assertTrue(holder.contains("private static int call0_2(int value)"), holder);
        assertTrue(constructionSource().contains("value -> value"));
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var callbacks = factory(loader, "demo.A", List.class);
            @SuppressWarnings("unchecked")
            final var retained = (java.util.function.IntFunction<java.util.function.IntUnaryOperator>) callbacks.get(0);
            assertEquals(42, retained.apply(7).applyAsInt(42));
            assertEquals(43, ((java.util.function.IntUnaryOperator) callbacks.get(1)).applyAsInt(43));
            assertEquals(44, ((java.util.function.IntUnaryOperator) callbacks.get(2)).applyAsInt(44));
        }
    }

    @Test
    void inlineHeavyPackageBoundsConstructionOwnersAndKeepsTypedFactories() throws Exception {
        final int artifacts = 128;
        final int callbacksPerFactory = 64;
        final var methods = new ArrayList<MethodSource>();
        final var references = new ArrayList<String>();
        for (int callback = 0; callback < callbacksPerFactory; callback++) {
            methods.add(new MethodSource(callback, "private static int $method() { return " + callback + "; }",
                    "() -> " + callback));
            references.add("(java.util.function.IntSupplier) $callback{" + callback + "}");
        }
        compileFragments(methods, "java.util.List.of(" + String.join(", ", references) + ")", artifacts,
                "classes", "java.util.List<java.util.function.IntSupplier>");
        final var facade = constructionSource();
        assertTrue(facade.contains("public static java.util.List<java.util.function.IntSupplier> A()"), facade);
        assertTrue(facade.contains("() -> 63"), facade);
        assertFalse(facade.contains("FusionFactories"), facade);
        int holders = 0;
        try (final var files = Files.walk(work.resolve("classes/demo"))) {
            for (final var file : files.filter(it -> it.getFileName().toString().startsWith("FusionBindings_") &&
                    it.toString().endsWith(".class")).toList()) {
                final var codes = codeLengths(file);
                assertTrue(codes.keySet().stream().filter(it -> it.startsWith("lambda$")).count() <= 32L * callbacksPerFactory, file.toString());
                assertTrue(codes.values().stream().allMatch(size -> size < 32_000), file.toString());
                holders++;
            }
        }
        assertTrue(holders > 1, "Overflow must remain sharded");
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var constructionMethods = GeneratedFactories.methods(loader, "demo", List.class);
            assertEquals(artifacts, constructionMethods.size());
            for (int artifact = 0; artifact < artifacts; artifact++) {
                final var name = artifact < 26 ? String.valueOf((char) ('A' + artifact)) : "Artifact" + artifact;
                assertEquals("java.util.List<java.util.function.IntSupplier>", constructionMethods.stream()
                        .filter(it -> it.getName().equals(name)).findFirst().orElseThrow().getGenericReturnType().getTypeName());
                final var callbacks = factory(loader, "demo." + name, List.class);
                assertEquals(callbacksPerFactory, callbacks.size());
                for (int callback = 0; callback < callbacksPerFactory; callback++) {
                    assertEquals(callback, ((java.util.function.IntSupplier) callbacks.get(callback)).getAsInt());
                }
            }
        }
    }

    @Test
    void testOutputStillUsesActualConstructionOwners() throws Exception {
        compileFragments(List.of(), "new Object()", 1, "custom-test-output", "Object");
        final var source = constructionSource();
        assertTrue(source.contains("public static Object A() { return new Object(); }"), source);
        assertFalse(Files.exists(work.resolve("generated/demo/FusionFactories.java")));
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("custom-test-output").toUri().toURL()}, getClass().getClassLoader())) {
            assertEquals(Object.class, factory(loader, "demo.A", Object.class).getClass());
        }
    }

    @Test
    void rejectsUnknownCallbackReferencesBeforeWritingSources() throws Exception {
        final var failure = assertThrows(RuntimeException.class, () -> compileFragments(List.of(),
                "(java.util.function.Supplier<String>) $callback{7}", 1));
        assertTrue(causes(failure).contains("Unknown callback reference: $callback{7}"), causes(failure));
        try (final var files = Files.walk(work.resolve("generated"))) {
            assertFalse(files.anyMatch(it -> it.getFileName().toString().startsWith("FusionBindings_")));
        }
    }

    @Test
    void rejectsDuplicateHelperIdentities() throws Exception {
        final var failure = assertThrows(RuntimeException.class, () -> compileFragments(List.of(
                new MethodSource(0, "private static String $method() { return null; }"),
                new MethodSource(0, "private static String $method() { return null; }")),
                "(java.util.function.Supplier<String>) $callback{0}", 1));
        assertTrue(causes(failure).contains("Duplicate helper identity"), causes(failure));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shortTypedHelpersPreserveConversionsAndSingleEvaluation() throws Exception {
        compileFragments(List.of(
                new MethodSource(0, "private static String $method(java.util.concurrent.atomic.AtomicInteger value) { return $helper{1}(value.incrementAndGet()); }"),
                new MethodSource(1, "private static String $method(int value) { return value + \":\" + value; }")),
                "(java.util.function.Function<java.util.concurrent.atomic.AtomicInteger, String>) $callback{0}", 2);
        assertTrue(holderSource().contains("private static String call0_0"));
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var callback = factory(loader, "demo.A", java.util.function.Function.class);
            final var counter = new java.util.concurrent.atomic.AtomicInteger();
            assertEquals("1:1", callback.apply(counter));
            assertEquals(1, counter.get());
            assertThrows(ClassCastException.class, () -> callback.apply("bad"));
        }
    }

    @Test
    void compilerReturnConversionsRemainInTypedHelpers() throws Exception {
        compile("private static Integer $method() { return demo.Input.value(); }",
                "(java.util.function.Supplier<Integer>) $callback{0}");
        assertTrue(holderSource().contains("private static Integer call0_0"));
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            final var callback = factory(loader, "demo.A", java.util.function.Supplier.class);
            assertThrows(ClassCastException.class, callback::get);
        }
    }

    @Test
    void largeDirectFamiliesBoundFactoryCodeAndKeepEveryTypedCallback() throws Exception {
        final var methods = new ArrayList<MethodSource>();
        final var references = new ArrayList<String>();
        for (int helper = 0; helper < 1200; helper++) {
            methods.add(new MethodSource(helper,
                    "private static String $method() { return \"value:" + helper + "\"; }"));
            references.add("(java.util.function.Supplier<String>) $callback{" + helper + "}");
        }
        compileFragments(methods, "java.util.List.of(" + String.join(", ", references) + ")", 8);
        int holders = 0;
        try (final var files = Files.walk(work.resolve("classes"))) {
            for (final var file : files.filter(it -> it.getFileName().toString().startsWith("FusionBindings_") &&
                    it.toString().endsWith(".class")).toList()) {
                final var codes = codeLengths(file);
                assertFalse(codes.containsKey("invoke"), codes.toString());
                assertFalse(codes.containsKey("factory"), codes.toString());
                assertEquals(1200, codes.keySet().stream().filter(it -> it.startsWith("call")).count());
                assertTrue(codes.values().stream().mapToInt(Integer::intValue).max().orElseThrow() < 32_000,
                        codes.toString());
                holders++;
            }
        }
        assertEquals(8, holders, "Large direct artifacts must remain in separate bounded holders");
        try (final var loader = new URLClassLoader(new java.net.URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader())) {
            for (int artifact = 0; artifact < 8; artifact++) {
                final var callbacks = factory(loader, "demo." + (char) ('A' + artifact), List.class);
                assertEquals(1200, callbacks.size());
                for (int helper = 0; helper < callbacks.size(); helper++) {
                    assertEquals("value:" + helper, ((java.util.function.Supplier<?>) callbacks.get(helper)).get());
                }
            }
        }
    }

    private <T> T descriptor(final ClassLoader loader, final int index, final Class<T> type) throws Exception {
        final var module = (io.yupiik.fusion.framework.api.container.FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
        final var descriptors = type == FunctionalBean.class ? module.beans().toList() : module.listeners().toList();
        return type.cast(descriptors.get(index));
    }

    private <T> T factory(final ClassLoader loader, final String id, final Class<T> type) throws Exception {
        final int separator = id.lastIndexOf('.');
        return type.cast(GeneratedFactories.invoke(loader, id.substring(0, separator), id.substring(separator + 1), Object.class));
    }

    private Map<String, Integer> codeLengths(final Path file) throws Exception {
        try (final var in = new DataInputStream(Files.newInputStream(file))) {
            assertEquals(0xcafebabe, in.readInt());
            in.skipNBytes(4);
            final var pool = new String[in.readUnsignedShort()];
            for (int index = 1; index < pool.length; index++) {
                switch (in.readUnsignedByte()) {
                    case 1 -> pool[index] = in.readUTF();
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> in.skipNBytes(4);
                    case 5, 6 -> { in.skipNBytes(8); index++; }
                    case 7, 8, 16, 19, 20 -> in.skipNBytes(2);
                    case 15 -> in.skipNBytes(3);
                    default -> throw new IllegalStateException("Unknown constant-pool entry");
                }
            }
            in.skipNBytes(6);
            in.skipNBytes(2L * in.readUnsignedShort());
            final var result = new HashMap<String, Integer>();
            for (int section = 0; section < 2; section++) {
                final int count = in.readUnsignedShort();
                for (int member = 0; member < count; member++) {
                    in.readUnsignedShort();
                    final var name = pool[in.readUnsignedShort()];
                    in.readUnsignedShort();
                    final int attributes = in.readUnsignedShort();
                    for (int attribute = 0; attribute < attributes; attribute++) {
                        final var kind = pool[in.readUnsignedShort()];
                        final int size = in.readInt();
                        if (section == 1 && "Code".equals(kind)) {
                            in.skipNBytes(4);
                            result.put(name, in.readInt());
                            in.skipNBytes(size - 8L);
                        } else {
                            in.skipNBytes(size);
                        }
                    }
                }
            }
            return result;
        }
    }

    private String causes(final Throwable failure) {
        return failure + (failure.getCause() == null ? "" : "\n" + causes(failure.getCause()));
    }

    private String holderSource() throws Exception {
        try (final var files = Files.walk(work.resolve("generated"))) {
            return Files.readString(files.filter(it -> it.getFileName().toString().startsWith("FusionBindings_")).findFirst().orElseThrow());
        }
    }

    private String constructionSource() throws Exception {
        final var source = new StringBuilder();
        try (final var files = Files.walk(work.resolve("generated"))) {
            for (final var file : files.filter(it -> it.toString().endsWith(".java")).sorted().toList()) {
                source.append(Files.readString(file));
            }
        }
        return source.toString();
    }

    private String constructionOwner() throws Exception {
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            return "demo." + files.filter(it -> it.getFileName().toString().startsWith("FusionBindings_"))
                    .findFirst().orElseThrow().getFileName().toString().replace(".java", "");
        }
    }

    private void compile(final String declaration, final String factory) throws Exception {
        compileFragments(declaration == null ? List.of() : List.of(new MethodSource(0, declaration)), factory, 2);
    }

    private void compileFragments(final List<MethodSource> methods, final String factory, final int artifacts) throws Exception {
        compileFragments(methods, factory, artifacts, "classes", "Object");
    }

    private void compileFragments(final List<MethodSource> methods, final String factory, final int artifacts,
                                  final String outputName, final String registrationType) throws Exception {
        compileFragments(methods, factory, artifacts, outputName, registrationType, null);
    }

    private void compileFragments(final List<MethodSource> methods, final String factory, final int artifacts,
                                  final String outputName, final String registrationType, final String kind) throws Exception {
        final var classes = Files.createDirectories(work.resolve(outputName));
        final var generated = Files.createDirectories(work.resolve("generated"));
        final var source = work.resolve("Input.java");
        Files.writeString(source, """
                package demo;
                public class Input {
                    public static int events;
                    @SuppressWarnings("unchecked")
                    public static <T> T value() { return (T) "not-an-integer"; }
                    public static class Settings {
                        static { events++; }
                        private final String value;
                        public Settings(String value) { this.value = value; }
                        public String toString() { return value; }
                    }
                }
                """);
        final var compiler = ToolProvider.getSystemJavaCompiler();
        final var diagnostics = new StringWriter();
        try (final var manager = compiler.getStandardFileManager(null, null, null)) {
            final var task = compiler.getTask(diagnostics, manager, null, List.of("--release", "17", "-classpath",
                    System.getProperty("java.class.path"), "-d", classes.toString(), "-s", generated.toString()), null,
                    manager.getJavaFileObjectsFromFiles(List.of(source.toFile())));
            task.setProcessors(List.of(new FragmentProcessor(work, classes, methods, factory, artifacts, registrationType, kind)));
            assertTrue(task.call(), diagnostics.toString());
        }
    }

    private record MethodSource(int operation, String declaration, String inlineExpression) {
        private MethodSource(final int operation, final String declaration) {
            this(operation, declaration, "");
        }
    }

    private static final class FragmentProcessor extends AbstractProcessor {
        private final Path work;
        private final Path output;
        private final List<MethodSource> methods;
        private final String factory;
        private final int artifacts;
        private final String registrationType;
        private final String kind;
        private boolean done;

        private FragmentProcessor(final Path work, final Path output, final List<MethodSource> methods, final String factory,
                                  final int artifacts, final String registrationType, final String kind) {
            this.work = work;
            this.output = output;
            this.methods = methods;
            this.factory = factory;
            this.artifacts = artifacts;
            this.registrationType = registrationType;
            this.kind = kind;
        }

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
            if (done || roundEnv.processingOver()) {
                return false;
            }
            done = true;
            try {
                final var loader = new ProcessorInternals(getClass().getClassLoader());
                final var prefix = "io.yupiik.fusion.framework.processor.internal.generator.";
                final var emitterType = loader.loadClass(prefix + "BindingEmitter");
                final var bindingType = loader.loadClass(prefix + "Binding");
                final var methodType = loader.loadClass(prefix + "Binding$Method");
                final var generatedType = loader.loadClass(prefix + "BaseGenerator$GeneratedClass");
                final var emitter = emitterType.getConstructor(javax.annotation.processing.ProcessingEnvironment.class, Path.class, Path.class)
                        .newInstance(processingEnv, work.resolve("state"), output);
                final var root = roundEnv.getRootElements().iterator().next();
                emitterType.getMethod("touch", javax.lang.model.element.Element.class).invoke(emitter, root);
                final var fragments = new ArrayList<Object>();
                for (final var method : methods) {
                    fragments.add(methodType.getConstructor(int.class, String.class, String.class)
                            .newInstance(method.operation(), method.declaration(), method.inlineExpression()));
                }
                final var binding = bindingType.getConstructor(String.class, String.class, String.class, String.class, List.class)
                        .newInstance("demo", kind, registrationType, factory, fragments);
                for (int index = 0; index < artifacts; index++) {
                    final var id = "demo." + (index < 26 ? String.valueOf((char) ('A' + index)) : "Artifact" + index);
                    final var fragment = generatedType.getConstructor(String.class, bindingType).newInstance(id, binding);
                    emitterType.getMethod("add", javax.lang.model.element.Element.class, generatedType).invoke(emitter, root, fragment);
                }
                final Consumer<Object> writer = value -> {
                    try {
                        final var name = (String) generatedType.getMethod("name").invoke(value);
                        final var content = (String) generatedType.getMethod("content").invoke(value);
                        try (final var out = processingEnv.getFiler().createSourceFile(name).openWriter()) {
                            out.write(content);
                        }
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                };
                ProcessorInternals.invoke(emitterType.getMethod("emit", Consumer.class, String.class), emitter, writer, kind == null ? null : "demo.Module");
                if (kind != null) {
                    final var ids = new ArrayList<String>();
                    for (int index = 0; index < artifacts; index++) {
                        ids.add("demo." + (index < 26 ? String.valueOf((char) ('A' + index)) : "Artifact" + index));
                    }
                    ids.sort(String::compareTo);
                    final var registrations = (List<?>) emitterType.getMethod("registrationCalls", List.class).invoke(emitter, ids);
                    final var helpers = (String) emitterType.getMethod("moduleHelpers").invoke(emitter);
                    try (final var out = processingEnv.getFiler().createSourceFile("demo.Module").openWriter()) {
                        out.write("package demo; public class Module implements io.yupiik.fusion.framework.api.container.FusionModule {\n" +
                                "public java.util.stream.Stream<" + registrationType + "> " + (kind.equals("bean") ? "beans" : "listeners") + "() {\n" +
                                "final var builder = java.util.stream.Stream.<" + registrationType + ">builder();\n" +
                                String.join("\n", registrations.stream().map(Object::toString).toList()) +
                                "\nreturn builder.build(); }\n" + helpers + "\n}");
                    }
                }
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
            return false;
        }
    }
}
