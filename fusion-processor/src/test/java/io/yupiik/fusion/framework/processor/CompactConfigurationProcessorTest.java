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
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.MissingRequiredParameterException;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactConfigurationProcessorTest {
    @TempDir
    Path work;

    @Test
    void typedFactoriesPreserveConversionsDefaultsDocsAndFreshValues() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(
                    @Property(required=true, documentation="Required name.") String name,
                    @Property(defaultValue="demo.Settings.eager()") int count,
                    boolean enabled, long big, float ratio, double precise,
                    java.math.BigInteger integer, java.math.BigDecimal decimal,
                    Mode mode, java.util.List<Integer> numbers, java.util.List<Mode> modes,
                    java.util.Map<String,Integer> properties, java.util.Map<String,Mode> modeProperties,
                    Child child,
                    @Property(defaultValue="demo.Settings.listDefault()") java.util.List<Child> children,
                    @Property(defaultValue="demo.Settings.mapDefault()") java.util.Map<String,Child> byName) {
                    private static int eagerCalls;
                    private static int lazyCalls;
                    public static int eager() { return ++eagerCalls; }
                    public static java.util.List<Child> listDefault() { lazyCalls++; return java.util.List.of(new Child("default")); }
                    public static java.util.Map<String,Child> mapDefault() { lazyCalls++; return java.util.Map.of("default", new Child("default")); }
                    public static String calls() { return eagerCalls + ":" + lazyCalls; }
                    public enum Mode {
                        ON, OFF;
                        public static Mode fromConfigurationString(String value) { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
                    }
                    public record Child(String value) {}
                }
                """));
        final var values = new HashMap<String, String>(Map.ofEntries(
                Map.entry("name", "configured"), Map.entry("count", "42"), Map.entry("enabled", "true"),
                Map.entry("big", "12345678901"), Map.entry("ratio", "1.5"), Map.entry("precise", "2.25"),
                Map.entry("integer", "12345678901234567890"), Map.entry("decimal", "2.30"), Map.entry("mode", "on"),
                Map.entry("numbers", "sep=; 1 ; ;2; 3"), Map.entry("modes", "on, off"),
                Map.entry("properties", "one=1\ntwo=2"), Map.entry("modeProperties", "first=on\nsecond=off"),
                Map.entry("child.value", "nested"), Map.entry("children.length", "1"), Map.entry("children.0.value", "indexed"),
                Map.entry("byName.length", "1"), Map.entry("byName.0.key", "key"), Map.entry("byName.0.value.value", "mapped")));
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            final var factory = factory(loader);
            assertInstanceOf(java.util.function.BiFunction.class, factory);
                assertTrue(factory.getClass().isHidden());
            final var first = factory.apply(configuration, Function.identity());
            final var second = factory.apply(configuration, Function.identity());
            assertNotSame(first, second);
            assertNotSame(value(first, "child"), value(second, "child"));
            assertEquals("configured", value(first, "name"));
            assertEquals(42, value(first, "count"));
            assertEquals(true, value(first, "enabled"));
            assertEquals(12345678901L, value(first, "big"));
            assertEquals(1.5f, value(first, "ratio"));
            assertEquals(2.25, value(first, "precise"));
            assertEquals("12345678901234567890", value(first, "integer").toString());
            assertEquals("2.30", value(first, "decimal").toString());
            assertEquals("ON", value(first, "mode").toString());
            assertEquals(List.of(1, 2, 3), value(first, "numbers"));
            assertEquals("[ON, OFF]", value(first, "modes").toString());
            assertEquals(Map.of("one", 1, "two", 2), value(first, "properties"));
            assertEquals("OFF", ((Map<?, ?>) value(first, "modeProperties")).get("second").toString());
            assertEquals("Child[value=nested]", value(first, "child").toString());
            assertEquals("[Child[value=indexed]]", value(first, "children").toString());
            assertEquals("{key=Child[value=mapped]}", value(first, "byName").toString());
            assertEquals("2:0", first.getClass().getMethod("calls").invoke(null));
            values.remove("children.length");
            values.put("byName.length", "-1");
            final var defaults = factory.apply(configuration, Function.identity());
            assertEquals("[Child[value=default]]", value(defaults, "children").toString());
            assertEquals("{default=Child[value=default]}", value(defaults, "byName").toString());
            assertEquals("3:2", first.getClass().getMethod("calls").invoke(null));
            values.remove("name");
            final var missing = assertThrows(MissingRequiredParameterException.class,
                    () -> factory.apply(configuration, key -> "cli-" + key));
            assertEquals("No value for 'cli-name'", missing.getMessage());
            values.put("name", "bean");
            final var module = (FusionModule) loader.loadClass("demo.Module").getConstructor().newInstance();
            final var beans = module.beans().toList();
            assertEquals(1, beans.size());
            assertInstanceOf(FunctionalBean.class, beans.get(0));
            assertEquals(first.getClass(), beans.get(0).type());
            assertEquals(loader.loadClass("demo.Settings"), beans.get(0).type());
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(module)
                    .register(new io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean<>(
                            io.yupiik.fusion.framework.api.scope.DefaultScoped.class,
                            io.yupiik.fusion.framework.api.configuration.ConfigurationSource.class, () -> values::get)).start();
                 final var instance = container.lookup(first.getClass())) {
                assertEquals("bean", value(instance.instance(), "name"));
            }
        }
        try (final var generated = Files.walk(work.resolve("generated"))) {
            for (final var file : generated.filter(it -> it.toString().endsWith(".java")).toList()) {
                final var source = Files.readString(file);
                assertFalse(source.contains("DispatchConfigurationFactory"), source);
                assertFalse(source.contains("DispatchBean"), source);
                assertFalse(source.contains("__NestedFactory"), source);
            }
        }
        assertHoldersUseDirectCallbacks();
        try (final var classes = Files.walk(work.resolve("classes/demo"))) {
            assertTrue(classes.filter(it -> it.toString().endsWith(".class")).count() <= 6,
                    "Root and nested model classes plus bounded construction owners");
        }
        final var docs = Files.readString(work.resolve("classes/META-INF/fusion/configuration/documentation.json"));
        assertTrue(docs.contains("Required name."), docs);
    }

    @Test
    void defaultsRetainRootAndNestedContextAndEvaluationTiming() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("app")
                public record Settings(
                    @Property(defaultValue="configuration.get(\\\"OPENAI_API_KEY\\\").orElse(\\\"\\\")") String apiKey,
                    @Property(defaultValue="keyMapper.apply(\\\"root\\\")") String mapped,
                    @Property(defaultValue="java.util.List.of(new demo.Settings.Item(configuration.get(keyMapper.apply(\\\"rootList\\\")).orElseThrow()))") java.util.List<Item> items,
                    @Property(defaultValue="java.util.Map.of(keyMapper.apply(\\\"rootMap\\\"), new demo.Settings.Item(configuration.get(\\\"rootMap\\\").orElseThrow()))") java.util.Map<String,Item> byName,
                    Child child) {
                    public record Item(@Property(defaultValue="configuration.get(keyMapper.apply(prefix)).orElseThrow()") String value) {}
                    public record Child(
                        @Property(defaultValue="configuration.get(keyMapper.apply(prefix + \\\".fallback\\\")).orElseThrow()") String scalar,
                        @Property(defaultValue="prefix") String path,
                        @Property(defaultValue="java.util.List.of(new demo.Settings.Item(configuration.get(keyMapper.apply(prefix + \\\".listDefault\\\")).orElseThrow()))") java.util.List<Item> items,
                        @Property(defaultValue="java.util.Map.of(keyMapper.apply(prefix), new demo.Settings.Item(configuration.get(prefix + \\\".mapDefault\\\").orElseThrow()))") java.util.Map<String,Item> byName) {}
                }
                """));
        final var values = new HashMap<String, String>(Map.ofEntries(
                Map.entry("app.apiKey", "configured"), Map.entry("app.mapped", "configured-map"),
                Map.entry("app.items.length", "0"), Map.entry("app.byName.length", "0"),
                Map.entry("app.child.scalar", "configured-child"), Map.entry("app.child.path", "configured-path"),
                Map.entry("app.child.items.length", "0"), Map.entry("app.child.byName.length", "0"),
                Map.entry("OPENAI_API_KEY", "secret"), Map.entry("mapped-rootList", "root-list"),
                Map.entry("rootMap", "root-map"), Map.entry("mapped-app.child.fallback", "child-scalar"),
                Map.entry("mapped-app.child.listDefault", "child-list"), Map.entry("app.child.mapDefault", "child-map")));
        final var reads = new HashMap<String, Integer>();
        final var mappings = new HashMap<String, Integer>();
        final Configuration configuration = key -> {
            reads.merge(key, 1, Integer::sum);
            return Optional.ofNullable(values.get(key));
        };
        final Function<String, String> mapper = key -> {
            mappings.merge(key, 1, Integer::sum);
            return "mapped-" + key;
        };
        try (final var loader = loader()) {
            final var factory = factory(loader);
            final var configured = factory.apply(configuration, mapper);
            assertEquals("configured", value(configured, "apiKey"));
            assertEquals("configured-map", value(configured, "mapped"));
            assertEquals("configured-child", value(value(configured, "child"), "scalar"));
            assertEquals("configured-path", value(value(configured, "child"), "path"));
            assertEquals(List.of(), value(configured, "items"));
            assertEquals(Map.of(), value(configured, "byName"));
            assertEquals(List.of(), value(value(configured, "child"), "items"));
            assertEquals(Map.of(), value(value(configured, "child"), "byName"));
            assertEquals(1, reads.get("OPENAI_API_KEY"));
            assertEquals(1, reads.get("mapped-app.child.fallback"));
            assertEquals(Map.of("root", 1, "app.child.fallback", 1), mappings);
            assertFalse(reads.containsKey("mapped-rootList"));
            assertFalse(reads.containsKey("rootMap"));
            assertFalse(reads.containsKey("mapped-app.child.listDefault"));
            assertFalse(reads.containsKey("app.child.mapDefault"));

            values.keySet().removeIf(key -> key.startsWith("app."));
            // Keep fallback data outside the configured property names.
            values.put("app.child.mapDefault", "child-map");
            final var defaults = factory.apply(configuration, mapper);
            assertEquals("secret", value(defaults, "apiKey"));
            assertEquals("mapped-root", value(defaults, "mapped"));
            assertEquals("[Item[value=root-list]]", value(defaults, "items").toString());
            assertEquals("{mapped-rootMap=Item[value=root-map]}", value(defaults, "byName").toString());
            final var child = value(defaults, "child");
            assertEquals("child-scalar", value(child, "scalar"));
            assertEquals("app.child", value(child, "path"));
            assertEquals("[Item[value=child-list]]", value(child, "items").toString());
            assertEquals("{mapped-app.child=Item[value=child-map]}", value(child, "byName").toString());
            assertEquals(2, reads.get("OPENAI_API_KEY"));
            assertEquals(2, reads.get("mapped-app.child.fallback"));
            assertEquals(1, reads.get("mapped-rootList"));
            assertEquals(1, reads.get("rootMap"));
            assertEquals(1, reads.get("mapped-app.child.listDefault"));
            assertEquals(1, reads.get("app.child.mapDefault"));
            values.put("app.items.length", "-1");
            values.put("app.byName.length", "-1");
            values.put("app.child.items.length", "-1");
            values.put("app.child.byName.length", "-1");
            factory.apply(configuration, mapper);
            assertEquals(3, reads.get("OPENAI_API_KEY"));
            assertEquals(2, reads.get("mapped-rootList"));
            assertEquals(2, reads.get("rootMap"));
            assertEquals(2, reads.get("mapped-app.child.listDefault"));
            assertEquals(2, reads.get("app.child.mapDefault"));
            values.put("app.items.length", "1");
            values.put("app.byName.length", "1");
            values.put("app.byName.0.key", "entry");
            values.put("mapped-app.items.0", "indexed-list");
            values.put("mapped-app.byName.0.value", "indexed-map");
            final var indexed = factory.apply(configuration, mapper);
            assertEquals("[Item[value=indexed-list]]", value(indexed, "items").toString());
            assertEquals("{entry=Item[value=indexed-map]}", value(indexed, "byName").toString());
            assertEquals(1, reads.get("mapped-app.items.0"));
            assertEquals(1, reads.get("mapped-app.byName.0.value"));
            assertEquals(2, reads.get("mapped-rootList"));
            assertEquals(2, reads.get("rootMap"));
        }
        assertHoldersUseDirectCallbacks();
    }

    @Test
    void contextReferencesDoNotCaptureUserLambdaParametersOrRewriteLiterals() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(
                    @Property(defaultValue="configuration.get(\\\"fallback\\\").orElseThrow() + java.util.stream.Stream.of(1).map(configuration -> configuration.toString()).findFirst().orElseThrow()") String mixed,
                    @Property(defaultValue="java.util.stream.Stream.of(2).map(configuration -> keyMapper.apply(configuration.toString())).findFirst().orElseThrow()") String configParameter,
                    @Property(defaultValue="java.util.stream.Stream.of(3).map(keyMapper -> configuration.get(keyMapper.toString()).orElseThrow()).findFirst().orElseThrow()") String mapperParameter,
                    @Property(defaultValue="java.util.stream.Stream.of(4).map(configuration -> { var keyMapper = configuration.toString(); return keyMapper; }).findFirst().orElseThrow()") String block,
                    @Property(defaultValue="/* configuration keyMapper prefix */ \\\"configuration keyMapper prefix\\\"") String literal,
                    Child child) {
                    public record Child(
                        @Property(defaultValue="prefix + java.util.stream.Stream.of(5).map(prefix -> prefix.toString()).findFirst().orElseThrow()") String mixed,
                        @Property(defaultValue="java.util.stream.Stream.of(6).map(prefix -> configuration.get(keyMapper.apply(prefix.toString())).orElseThrow()).findFirst().orElseThrow()") String parameter) {}
                }
                """));
        final var values = Map.of("fallback", "fallback-", "3", "three", "mapped-6", "six");
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            final var result = factory(loader).apply(configuration, key -> "mapped-" + key);
            assertEquals("fallback-1", value(result, "mixed"));
            assertEquals("mapped-2", value(result, "configParameter"));
            assertEquals("three", value(result, "mapperParameter"));
            assertEquals("4", value(result, "block"));
            assertEquals("configuration keyMapper prefix", value(result, "literal"));
            assertEquals("child5", value(value(result, "child"), "mixed"));
            assertEquals("six", value(value(result, "child"), "parameter"));
        }
    }

    @Test
    void explicitThisRetainsRootAndNestedContext() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(
                    @Property(defaultValue="this.configuration.get(this.keyMapper.apply(\\\"fallback\\\")).orElseThrow()") String value,
                    Child child) {
                    public record Child(
                        @Property(defaultValue="this.configuration.get(this.keyMapper.apply(this.prefix)).orElseThrow()") String value) {}
                }
                """));
        final var values = Map.of("mapped-fallback", "root", "mapped-child", "nested");
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            final var result = factory(loader).apply(configuration, key -> "mapped-" + key);
            assertEquals("root", value(result, "value"));
            assertEquals("nested", value(value(result, "child"), "value"));
        }
    }

    @Test
    void catchParametersShadowDefaultContextOnlyInTheirCatch() throws Exception {
        assertDefaultScope("java.util.stream.Stream.of(1).map(v -> { try { throw new IllegalArgumentException(\"value\"); } catch (final IllegalArgumentException configuration) { return configuration.getMessage(); } }).findFirst().orElse(\"\")", "value");
    }

    @Test
    void enhancedForVariablesDoNotShadowTheirIterableOrFollowingStatements() throws Exception {
        assertDefaultScope("java.util.stream.Stream.of(1).map(v -> { var result = \"\"; for (final String configuration : java.util.List.of(\"loop\")) { result += configuration.toUpperCase(java.util.Locale.ROOT); } return result; }).findFirst().orElseThrow()", "LOOP");
    }

    @Test
    void forInitializersAreScopedThroughConditionUpdateAndBody() throws Exception {
        assertDefaultScope("java.util.stream.Stream.of(1).map(v -> { var result = \"\"; for (int configuration = 0, keyMapper = configuration + 1; configuration < 2; configuration++, keyMapper++) { result += keyMapper; } return result; }).findFirst().orElseThrow()", "12");
    }

    @Test
    void tryResourcesAreScopedThroughLaterResourcesAndBodyOnly() throws Exception {
        assertDefaultScope("java.util.stream.Stream.of(1).map(v -> { try (var configuration = new java.io.StringReader(\"resource\"); var keyMapper = new java.io.BufferedReader(configuration)) { return keyMapper.readLine(); } catch (java.io.IOException e) { throw new IllegalStateException(e); } }).findFirst().orElseThrow()", "resource");
    }

    @Test
    void lexicalScopesStillAllowFreeContextReferencesInTheSameLambda() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(
                    @Property(defaultValue="java.util.stream.Stream.of(1).map(v -> { var result = new StringBuilder(); for (String configuration : java.util.List.of(configuration.get(keyMapper.apply(\\\"fallback\\\")).orElseThrow())) { result.append(configuration.toUpperCase(java.util.Locale.ROOT)); } for (int configuration = 0; configuration < 1; configuration++) { result.append(configuration); } try (var configuration = new java.io.StringReader(\\\"x\\\")) { result.append((char) configuration.read()); } catch (java.io.IOException e) { throw new IllegalStateException(e); } try { throw new IllegalArgumentException(\\\"catch\\\"); } catch (IllegalArgumentException configuration) { result.append(configuration.getMessage()); } return result + configuration.get(keyMapper.apply(\\\"fallback\\\")).orElseThrow(); }).findFirst().orElseThrow()") String value) {}
                """));
        final var values = Map.of("mapped-fallback", "outer");
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            assertEquals("OUTER0xcatchouter", value(factory(loader).apply(configuration, key -> "mapped-" + key), "value"));
        }
    }

    @Test
    void resourceNamesDoNotHideContextInCatchOrFinally() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(
                    @Property(defaultValue="java.util.stream.Stream.of(1).map(v -> { var result = new StringBuilder(); try (var configuration = new java.io.StringReader(\\\"x\\\")) { result.append((char) configuration.read()); throw new java.io.IOException(); } catch (java.io.IOException e) { result.append(configuration.get(\\\"fallback\\\").orElseThrow()); } finally { result.append(keyMapper.apply(\\\"finally\\\")); } return result.toString(); }).findFirst().orElseThrow()") String value) {}
                """));
        final var values = Map.of("fallback", "outer");
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            assertEquals("xoutermapped-finally", value(factory(loader).apply(configuration, key -> "mapped-" + key), "value"));
        }
    }

    @Test
    void contextNamedBindingPatternsKeepFlowScopes() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(
                    @Property(defaultValue="java.util.stream.Stream.<Object>of(\\\"pattern\\\", 1).map(v -> v instanceof String configuration ? configuration.toUpperCase(java.util.Locale.ROOT) : configuration.get(\\\"fallback\\\").orElseThrow()).collect(java.util.stream.Collectors.joining(\\\":\\\"))") String conditional,
                    @Property(defaultValue="java.util.stream.Stream.<Object>of(\\\"pattern\\\", 1).map(v -> { if (!(v instanceof String keyMapper)) { return keyMapper.apply(\\\"fallback\\\"); } return keyMapper.toUpperCase(java.util.Locale.ROOT); }).collect(java.util.stream.Collectors.joining(\\\":\\\"))") String guard,
                    Child child) {
                    public record Child(
                        @Property(defaultValue="java.util.stream.Stream.<Object>of(\\\"pattern\\\", 1).map(v -> { if (v instanceof String prefix && !prefix.isEmpty()) { return prefix; } return prefix; }).collect(java.util.stream.Collectors.joining(\\\":\\\"))") String value) {}
                }
                """));
        final Configuration configuration = key -> "fallback".equals(key) ? Optional.of("outer") : Optional.empty();
        try (final var loader = loader()) {
            final var result = factory(loader).apply(configuration, key -> "mapped-" + key);
            assertEquals("PATTERN:outer", value(result, "conditional"));
            assertEquals("PATTERN:mapped-fallback", value(result, "guard"));
            assertEquals("pattern:child", value(value(result, "child"), "value"));
        }
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    void switchPatternsUseTheActiveApplicationLanguageAndContext() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(
                    @Property(defaultValue="switch ((Object) configuration.get(\\\"fallback\\\").orElseThrow()) { case String configuration when !configuration.isEmpty() -> configuration + this.keyMapper.apply(\\\"suffix\\\"); default -> this.configuration.get(\\\"empty\\\").orElseThrow(); }") String value,
                    Child child) {
                    public record Child(
                        @Property(defaultValue="switch ((Object) configuration.get(\\\"fallback\\\").orElseThrow()) { case String prefix when !prefix.isEmpty() -> prefix + this.prefix; default -> this.prefix; }") String value) {}
                }
                """));
        final var values = new HashMap<>(Map.of("fallback", "pattern", "empty", "outer"));
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            final var factory = factory(loader);
            final var matched = factory.apply(configuration, key -> "mapped-" + key);
            assertEquals("patternmapped-suffix", value(matched, "value"));
            assertEquals("patternchild", value(value(matched, "child"), "value"));
            values.put("fallback", "");
            final var unmatched = factory.apply(configuration, key -> "mapped-" + key);
            assertEquals("outer", value(unmatched, "value"));
            assertEquals("child", value(value(unmatched, "child"), "value"));
        }
        assertHoldersUseDirectCallbacks();
    }

    @Test
    void bindingPatternsWithOtherNamesKeepContextReferences() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(
                    @Property(defaultValue="java.util.stream.Stream.<Object>of(\\\"pattern\\\").map(v -> v instanceof String text && !text.isEmpty() ? text + configuration.get(keyMapper.apply(\\\"fallback\\\")).orElseThrow() : \\\"\\\").findFirst().orElseThrow()") String value) {}
                """));
        final var values = Map.of("mapped-fallback", "outer");
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            assertEquals("patternouter", value(factory(loader).apply(configuration, key -> "mapped-" + key), "value"));
        }
    }

    private void assertDefaultScope(final String expression, final String expected) throws Exception {
        // Compile both the former instance-field boundary and a context-free baseline
        // alongside the generated defaults, then compare observable values.
        final var mixed = "configuration.get(keyMapper.apply(\"fallback\")).orElseThrow() + " + expression;
        final var nested = "prefix + " + mixed;
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(@Property(defaultValue="%s") String plain,
                                       @Property(defaultValue="%s") String mixed, Child child) {
                    public static String baseline() { return %s; }
                    public record Child(@Property(defaultValue="%s") String value) {}
                    public static class Legacy {
                        private final io.yupiik.fusion.framework.api.configuration.Configuration configuration;
                        private final java.util.function.Function<String, String> keyMapper;
                        public Legacy(io.yupiik.fusion.framework.api.configuration.Configuration c,
                                      java.util.function.Function<String, String> k) { configuration = c; keyMapper = k; }
                        public String get() { return %s; }
                    }
                }
                """.formatted(expression.replace("\\", "\\\\").replace("\"", "\\\""),
                mixed.replace("\\", "\\\\").replace("\"", "\\\""), expression,
                nested.replace("\\", "\\\\").replace("\"", "\\\""), mixed)));
        final Configuration configuration = key -> Optional.ofNullable("mapped-fallback".equals(key) ? "context-" : null);
        try (final var generated = Files.walk(work.resolve("generated"))) {
            var unchanged = false;
            for (final var file : generated.filter(it -> it.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("_default() { return " + expression + "; }")) {
                    unchanged = true;
                    break;
                }
            }
            assertTrue(unchanged, "Defaults must remain unchanged in parameterless instance helpers");
        }
        final Function<String, String> mapper = key -> "mapped-" + key;
        try (final var loader = loader()) {
            final var result = factory(loader).apply(configuration, mapper);
            assertEquals(expected, result.getClass().getMethod("baseline").invoke(null));
            assertEquals(expected, value(result, "plain"));
            final var legacy = loader.loadClass("demo.Settings$Legacy").getConstructor(Configuration.class, Function.class)
                    .newInstance(configuration, mapper);
            assertEquals("context-" + expected, value(legacy, "get"));
            assertEquals("context-" + expected, value(result, "mixed"));
            assertEquals("childcontext-" + expected, value(value(result, "child"), "value"));
        }
    }

    @Test
    void userDefaultLambdasHaveIndependentScopeAndKeepEvaluationTiming() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(
                    @Property(defaultValue="java.util.stream.Stream.of(1).map(value -> demo.Settings.tick(0)).findFirst().orElse(0)") int scalar,
                    @Property(defaultValue="java.util.stream.Stream.of(1).map(raw -> demo.Settings.tick(1)).toList()") java.util.List<Integer> raw,
                    @Property(defaultValue="java.util.stream.Stream.of(1).map(converted -> demo.Settings.tick(2)).toList()") java.util.List<Integer> converted,
                    @Property(defaultValue="java.util.stream.Stream.of(1).collect(java.util.stream.Collectors.toMap(value -> value.toString(), value -> demo.Settings.tick(3)))") java.util.Map<String,Integer> mapped,
                    @Property(defaultValue="java.util.stream.Stream.of(1).map(length -> new demo.Settings.Child(java.lang.Integer.toString(demo.Settings.tick(4)))).toList()") java.util.List<Child> children,
                    @Property(defaultValue="java.util.stream.Stream.of(1).collect(java.util.stream.Collectors.toMap(size -> size.toString(), size -> new demo.Settings.Child(java.lang.Integer.toString(demo.Settings.tick(5)))))") java.util.Map<String,Child> byName,
                    @Property(defaultValue="java.util.stream.Stream.of(1).map(configuration -> new demo.Settings.Child(configuration.toString())).findFirst().orElseThrow()") Child selected,
                    @Property(defaultValue="new demo.Settings.Child(configuration.get(keyMapper.apply(\\\"fallback\\\")).orElse(\\\"context\\\"))") Child contextual) {
                    private static final int[] calls = new int[6];
                    public static int tick(int index) { return ++calls[index]; }
                    public static String calls() { return java.util.Arrays.toString(calls); }
                    public record Child(String value) {}
                }
                """));
        final var values = new HashMap<>(Map.of("scalar", "42", "raw", "2,3", "converted", "4,5", "mapped", "configured=6",
                "children.length", "0", "byName.length", "0"));
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            final var factory = factory(loader);
            final var configured = factory.apply(configuration, Function.identity());
            assertEquals(42, value(configured, "scalar"));
            assertEquals(List.of(2, 3), value(configured, "raw"));
            assertEquals(List.of(4, 5), value(configured, "converted"));
            assertEquals(Map.of("configured", 6), value(configured, "mapped"));
            assertEquals(List.of(), value(configured, "children"));
            assertEquals(Map.of(), value(configured, "byName"));
            assertEquals("Child[value=1]", value(configured, "selected").toString());
            assertEquals("Child[value=context]", value(configured, "contextual").toString());
            assertEquals("[1, 1, 1, 1, 0, 0]", configured.getClass().getMethod("calls").invoke(null));
            values.put("scalar", "not-an-integer");
            assertThrows(NumberFormatException.class, () -> factory.apply(configuration, Function.identity()));
            assertEquals("[1, 1, 1, 1, 0, 0]", configured.getClass().getMethod("calls").invoke(null));
            values.clear();
            final var defaults = factory.apply(configuration, Function.identity());
            assertEquals(2, value(defaults, "scalar"));
            assertEquals(List.of(2), value(defaults, "raw"));
            assertEquals(List.of(2), value(defaults, "converted"));
            assertEquals(Map.of("1", 2), value(defaults, "mapped"));
            assertEquals("[Child[value=1]]", value(defaults, "children").toString());
            assertEquals("{1=Child[value=1]}", value(defaults, "byName").toString());
            assertEquals("[2, 2, 2, 2, 1, 1]", configured.getClass().getMethod("calls").invoke(null));
            values.put("children.length", "-1");
            values.put("byName.length", "-1");
            factory.apply(configuration, Function.identity());
            assertEquals("[3, 3, 3, 3, 2, 2]", configured.getClass().getMethod("calls").invoke(null));
        }
    }

    @Test
    void unicodeEscapedQuotesDoNotInlineAnArbitraryExpression() throws Exception {
        final var expression = "\"start" + "\\u0022" +
                " + java.util.stream.Stream.of(1).map(configuration -> configuration.toString()).findFirst().orElseThrow() + " +
                "\\u0022" + "end\"";
        final var escaped = expression.replace("\\", "\\\\").replace("\"", "\\\"");
        compile(Map.of("demo.Settings", "package demo; " +
                "@io.yupiik.fusion.framework.build.api.configuration.RootConfiguration(\"-\") " +
                "public record Settings(@io.yupiik.fusion.framework.build.api.configuration.Property(defaultValue=\"" + escaped + "\") String value) {}"));
        try (final var loader = loader()) {
            final var result = factory(loader).apply(key -> Optional.empty(), Function.identity());
            assertEquals("start1end", value(result, "value"));
        }
    }

    @Test
    void sharedBuiltinsAvoidPerPropertyMethodsAndLiteralDefaultHelpers() throws Exception {
        final var parameters = IntStream.range(0, 80)
                .mapToObj(it -> "@io.yupiik.fusion.framework.build.api.configuration.Property(defaultValue=\"7\") int n" + it)
                .collect(joining(", "));
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(%s,
                    @Property(defaultValue="7") long widenedLong,
                    @Property(defaultValue="7") float widenedFloat,
                    @Property(defaultValue="7") double widenedDouble,
                    @Property(defaultValue="\\\"label\\\"") CharSequence label,
                    @Property(defaultValue="\\\"%s\\\"") String large,
                    java.util.List<CharSequence> labels, java.util.Map<String,CharSequence> names) {}
                """.formatted(parameters, "x".repeat(20000))));
        final var values = Map.of("n79", "79", "labels", "a,b", "names", "first=a");
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            final var result = factory(loader).apply(configuration, Function.identity());
            for (int index = 0; index < 80; index++) {
                assertEquals(index == 79 ? 79 : 7, value(result, "n" + index));
            }
            assertEquals(7L, value(result, "widenedLong"));
            assertEquals(7.f, value(result, "widenedFloat"));
            assertEquals(7., value(result, "widenedDouble"));
            assertEquals("label", value(result, "label"));
            assertEquals("x".repeat(20000), value(result, "large"));
            assertEquals(List.of("a", "b"), value(result, "labels"));
            assertEquals(Map.of("first", "a"), value(result, "names"));
        }
        try (final var files = Files.walk(work.resolve("generated"))) {
            for (final var file : files.filter(it -> it.toString().endsWith(".java")).toList()) {
                final var source = Files.readString(file);
                assertFalse(source.contains("_property"), source);
                assertFalse(source.contains("_default"), source);
                assertFalse(source.contains("_DefaultContext"), source);
            }
        }
        assertHoldersUseDirectCallbacks();
    }

    @Test
    void numericLiteralInliningDoesNotLegalizeNarrowingDefaults() throws Exception {
        final var diagnostics = compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(@Property(defaultValue="1.5") int fractional,
                    @Property(defaultValue="2147483648L") int overflowing,
                    @Property(defaultValue="1.5d") float narrowing) {}
                """), false);
        assertTrue(diagnostics.contains("lossy conversion"), diagnostics);
    }

    @Test
    void nullableCustomEnumsKeepScalarListAndMapSemantics() throws Exception {
        compile(Map.of("demo.Settings", """
                package demo;
                import io.yupiik.fusion.framework.build.api.configuration.*;
                @RootConfiguration("-")
                public record Settings(@Property(defaultValue="demo.Settings.Mode.ON") Mode scalar,
                    java.util.List<Mode> list, java.util.Map<String,Mode> map) {
                    public enum Mode {
                        ON;
                        public static Mode fromConfigurationString(String value) {
                            if (value.equals("null")) { return null; }
                            if (value.equals("bad")) { throw new IllegalArgumentException("custom failure"); }
                            return valueOf(value);
                        }
                    }
                }
                """));
        final var values = new HashMap<>(Map.of("scalar", "null", "list", "ON,null", "map", "first=ON"));
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            final var factory = factory(loader);
            final var configured = factory.apply(configuration, Function.identity());
            assertEquals("ON", value(configured, "scalar").toString());
            final var list = (List<?>) value(configured, "list");
            assertEquals("[ON, null]", list.toString());
            assertThrows(UnsupportedOperationException.class, list::clear);
            values.put("map", "first=null");
            assertThrows(NullPointerException.class, () -> factory.apply(configuration, Function.identity()));
            values.put("scalar", "bad");
            assertEquals("custom failure", assertThrows(IllegalArgumentException.class,
                    () -> factory.apply(configuration, Function.identity())).getMessage());
            values.clear();
            final var absent = factory.apply(configuration, Function.identity());
            assertEquals("Settings[scalar=ON, list=null, map=null]", absent.toString());
        }
    }

    @Test
    void boundedStandaloneFactoriesWithoutModuleOrBean() throws Exception {
        final var records = IntStream.range(0, 40).mapToObj(it -> "@io.yupiik.fusion.framework.build.api.configuration.RootConfiguration(\"c" + it + "\") public record C" + it + "(@io.yupiik.fusion.framework.build.api.configuration.Property(defaultValue=\"java.lang.String.valueOf(7)\") String value, Mode mode) {}")
                .collect(joining("\n"));
        compile(Map.of("demo.Many", "package demo; public class Many { public enum Mode { ON, OFF } " + records + "}"),
                "-Afusion.generateModule=false", "-Afusion.generateBeanForRootConfiguration=false");
        try (final var loader = loader()) {
            assertEquals(40, GeneratedFactories.methods(loader, "demo", BiFunction.class).size());
            assertFalse(Files.exists(work.resolve("classes/META-INF/services/io.yupiik.fusion.framework.api.spi.dispatch.BindingProvider")));
            try (final var sources = Files.list(work.resolve("generated/demo"))) {
                final var owners = sources.filter(it -> it.toString().endsWith(".java")).toList();
                assertTrue(owners.size() > 1 && owners.size() <= 3,
                        "Forty standalone roots must split into bounded construction owners: " + owners);
            }
            final var values = IntStream.range(0, 40).boxed().collect(java.util.stream.Collectors.toMap(
                    it -> "c" + it + ".value", it -> "value-" + it));
            for (int index = 0; index < 40; index++) {
                values.put("c" + index + ".mode", index % 2 == 0 ? "ON" : "OFF");
            }
            final Configuration configuration = key -> Optional.ofNullable(values.get(key));
            for (int index = 0; index < 40; index++) {
                @SuppressWarnings("unchecked")
                final var factory = (BiFunction<Configuration, Function<String, String>, Object>)
                        GeneratedFactories.invoke(loader, "demo", "Many$C" + index + "$FusionConfigurationFactory", BiFunction.class);
                assertInstanceOf(java.util.function.BiFunction.class, factory);
            assertTrue(factory.getClass().isHidden());
                final var result = factory.apply(configuration, Function.identity());
                assertEquals("demo.Many$C" + index, result.getClass().getName());
                assertEquals("value-" + index, value(result, "value"));
                assertEquals(index % 2 == 0 ? "ON" : "OFF", value(result, "mode").toString());
            }
        }
        assertFalse(Files.exists(work.resolve("classes/demo/Module.class")));
        try (final var classes = Files.walk(work.resolve("classes/demo"))) {
            assertTrue(classes.filter(it -> it.toString().endsWith(".class")).count() <= 45,
                    "Forty roots must not emit per-root implementation classes");
        }
        assertHoldersUseDirectCallbacks();

    }

    @Test
    void prefixedModelsKeepDistinctNestedTypesWithTheSameSimpleName() throws Exception {
        compile(Map.of("demo.Prefixed", """
                package demo;
                @io.yupiik.fusion.framework.build.api.configuration.RootConfiguration("app")
                public record Prefixed(Left.Item left, Right.Item right, java.util.List<State> states) {
                    public enum State { ON, OFF }
                    public static class Left { public record Item(String value) {} }
                    public static class Right { public record Item(String value) {} }
                }
                """));
        final var values = Map.of("app.left.value", "left", "app.right.value", "right", "app.states", "ON, OFF");
        final Configuration configuration = key -> Optional.ofNullable(values.get(key));
        try (final var loader = loader()) {
            @SuppressWarnings("unchecked")
            final var factory = (BiFunction<Configuration, Function<String, String>, Object>)
                    GeneratedFactories.invoke(loader, "demo", "Prefixed$FusionConfigurationFactory", BiFunction.class);
            final var result = factory.apply(configuration, Function.identity());
            assertEquals("left", value(value(result, "left"), "value"));
            assertEquals("right", value(value(result, "right"), "value"));
            assertEquals("[ON, OFF]", value(result, "states").toString());
        }
        assertHoldersUseDirectCallbacks();
    }

    private Object value(final Object record, final String name) throws Exception {
        return record.getClass().getMethod(name).invoke(record);
    }

    @SuppressWarnings("unchecked")
    private BiFunction<Configuration, Function<String, String>, Object> factory(final ClassLoader loader) throws Exception {
        return GeneratedFactories.invoke(loader, "demo", "Settings$FusionConfigurationFactory", BiFunction.class);
    }

    private URLClassLoader loader() throws Exception {
        return new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader());
    }

    private void assertHoldersUseDirectCallbacks() throws Exception {
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            for (final var file : files.filter(it -> it.toString().endsWith(".java")).toList()) {
                assertDirectCallbacks("demo." + file.getFileName().toString().replace(".java", ""));
            }
        }
    }

    private void assertDirectCallbacks(final String type) {
        final var output = new StringWriter();
        final var tool = java.util.spi.ToolProvider.findFirst("javap").orElseThrow();
        assertEquals(0, tool.run(new PrintWriter(output), new PrintWriter(output), "-v", "-p", "-classpath",
                work.resolve("classes") + File.pathSeparator + System.getProperty("java.class.path"), type), output.toString());
        assertFalse(output.toString().lines().anyMatch(line -> line.contains("invokeinterface") && line.contains("Dispatch.invoke:")), output.toString());
        assertFalse(output.toString().contains("java/lang/reflect/Method"), output.toString());
    }

    private void compile(final Map<String, String> sources, final String... extra) throws Exception {
        compile(sources, true, extra);
    }

    private String compile(final Map<String, String> sources, final boolean success, final String... extra) throws Exception {
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
            // Compile fixtures with the active application compiler and its language level.
            final var options = new ArrayList<>(List.of("-classpath", System.getProperty("java.class.path"),
                    "-d", work.resolve("classes").toString(), "-s", work.resolve("generated").toString(),
                    "-Afusion.workdir=" + work.resolve("state"), "-Afusion.moduleFqn=demo.Module", "-Xlint:all,-processing,-serial"));
            options.addAll(List.of(extra));
            final var task = compiler.getTask(output, manager, null, options, null, manager.getJavaFileObjectsFromPaths(paths));
            task.setProcessors(List.of(new FusionProcessor()));
            assertEquals(success, task.call(), output.toString());
        }
        return output.toString();
    }
}
