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

import io.yupiik.fusion.cli.CliAwaiter;
import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.RuntimeContainerImpl;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.container.context.ApplicationFusionContext;
import io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext;
import io.yupiik.fusion.framework.api.container.context.subclass.SupplierDelegatingContext;
import io.yupiik.fusion.framework.api.main.Args;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.processor.test.CompilationClassLoader;
import io.yupiik.fusion.framework.processor.test.Compiler;
import io.yupiik.fusion.http.server.api.Body;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.impl.flow.BytesPublisher;
import io.yupiik.fusion.http.server.impl.io.RequestBodyAggregator;
import io.yupiik.fusion.http.server.spi.Endpoint;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.internal.codec.ObjectJsonCodec;
import io.yupiik.fusion.json.internal.formatter.SimplePrettyFormatter;
import io.yupiik.fusion.jsonrpc.JsonRpcEndpoint;
import io.yupiik.fusion.persistence.api.Database;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;
import io.yupiik.fusion.persistence.impl.translation.DefaultTranslation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class FusionProcessorTest {
    @Test
    void reuseParentConstructorInSubClass(@TempDir final Path work) throws IOException {
        final var compiler = new Compiler(work, "AppScopedBeanWithoutSubClassFriendlyConstructor");
        compiler.compileAndAsserts((loader, container) -> {
            try {
                assertEquals("""
                        package test.p;
                                                
                        @io.yupiik.fusion.framework.api.container.Generation(version = 1)
                        class AppScopedBeanWithoutSubClassFriendlyConstructor$FusionSubclass extends AppScopedBeanWithoutSubClassFriendlyConstructor {
                          private final io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext<AppScopedBeanWithoutSubClassFriendlyConstructor> fusionContext;
                                                
                          AppScopedBeanWithoutSubClassFriendlyConstructor$FusionSubclass(final io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext<AppScopedBeanWithoutSubClassFriendlyConstructor> context) {
                            super(null);
                            this.fusionContext = context;
                          }
                                                
                          @Override
                          public java.lang.String get() {
                            return this.fusionContext.instance().get();
                          }
                        }
                                                
                        """, Files.readString(compiler.getGeneratedSources().resolve("test/p/AppScopedBeanWithoutSubClassFriendlyConstructor$FusionSubclass.java")));
            } catch (final IOException e) {
                fail(e);
            }

            try (final Instance<Supplier<String>> instance = container.lookup(new Types.ParameterizedTypeImpl(Supplier.class, String.class))) {
                assertEquals("success", instance.instance().get());
            }
        });
    }

    @Test
    void simple(@TempDir final Path work) throws IOException {
        final var compiler = new Compiler(work, "Bean1", "Bean2");
        compiler.compileAndAsserts((loader, container) -> {
            try {
                assertEquals("""
                        package test.p;
                                                
                        @io.yupiik.fusion.framework.api.container.Generation(version = 1)
                        public class Bean1$FusionBean extends io.yupiik.fusion.framework.api.container.bean.BaseBean<Bean1> {
                          public Bean1$FusionBean() {
                            super(Bean1.class, io.yupiik.fusion.framework.api.scope.ApplicationScoped.class, 1000, java.util.Map.of("fusion.framework.subclasses.delegate",
                        (java.util.function.Function<io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext<Bean1>, Bean1>)
                          context -> new test.p.Bean1$FusionSubclass(context)
                        ));
                          }
                                                
                          @Override
                          public Bean1 create(final io.yupiik.fusion.framework.api.RuntimeContainer container, final java.util.List<io.yupiik.fusion.framework.api.Instance<?>> dependents) {
                            final var instance = new Bean1();
                            inject(container, dependents, instance);
                            return instance;
                          }
                                                
                          @Override
                          public void inject(final io.yupiik.fusion.framework.api.RuntimeContainer container, final java.util.List<io.yupiik.fusion.framework.api.Instance<?>> dependents, final Bean1 instance) {
                            instance.bean2 = lookup(container, test.p.Bean2.class, dependents);
                          }
                        }
                                                
                        """, Files.readString(compiler.getGeneratedSources().resolve("test/p/Bean1$FusionBean.java")));
                assertEquals("""
                        package test.p;
                                        
                        @io.yupiik.fusion.framework.api.container.Generation(version = 1)
                        class Bean1$FusionSubclass extends Bean1 {
                          private final io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext<Bean1> fusionContext;
                                        
                          Bean1$FusionSubclass(final io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext<Bean1> context) {
                            this.fusionContext = context;
                          }
                                        
                          @Override
                          public java.lang.String toString() {
                            return this.fusionContext.instance().toString();
                          }
                        }
                                        
                        """, Files.readString(compiler.getGeneratedSources().resolve("test/p/Bean1$FusionSubclass.java")));
                assertEquals("""
                        package test.p;
                                        
                        import java.util.stream.Stream;
                        import io.yupiik.fusion.framework.api.container.FusionBean;
                        import io.yupiik.fusion.framework.api.container.FusionModule;
                                        
                        public class FusionGeneratedModule implements FusionModule {
                            @Override
                            public Stream<FusionBean<?>> beans() {
                                return Stream.of(
                                    new test.p.Bean1$FusionBean(),
                                    new test.p.Bean2$FusionBean()
                                );
                            }
                            
                        }
                                        
                        """, Files.readString(compiler.getGeneratedSources().resolve("test/p/FusionGeneratedModule.java")));
                assertEquals("test.p.FusionGeneratedModule", Files.readString(compiler.getClasses().resolve("META-INF/services/" + FusionModule.class.getName())));
            } catch (final IOException ioe) {
                fail(ioe);
            }
            assertEquals("bean1[bean2[]]", container.lookup(loader.apply("test.p.Bean1")).instance().toString());
        });
    }

    @Test
    void listInjection(@TempDir final Path work) throws IOException {
        new Compiler(work, "Bean11", "Bean21", "Bean22").compileAndAsserts((loader, container) ->
                assertEquals("bean1{[bean21, bean22]}", container.lookup(loader.apply("test.p.Bean11")).instance().toString()));
    }

    @Test
    void listAutoSortedInjection(@TempDir final Path work) throws IOException {
        new Compiler(work, "InjectedListAutoSorted", "OrderedBean1", "OrderedBean2").compileAndAsserts((loader, container) ->
                assertEquals("bean1{[bean1, bean2]}", container.lookup(loader.apply("test.p.InjectedListAutoSorted")).instance().toString()));
    }

    @Test
    void lifecycle(@TempDir final Path work) throws IOException {
        final var ref = new AtomicReference<Instance<?>>();
        new Compiler(work, "Lifecycled", "LifecycledDep").compileAndAsserts((loader, container) -> {
            final var instance = container.lookup(loader.apply("test.p.LifecycledDep"));
            ref.set(instance);
            assertEquals("init=1, destroyed=0", instance.instance().toString());
            instance.close();
            // bean is app scoped so not destroyed there
            assertEquals("init=1, destroyed=1", instance.instance().toString());
        });
        // here the bean is destroyed by the container
        assertEquals("init=1, destroyed=1", ref.get().instance().toString());
    }

    @Test
    void nestedBeans(@TempDir final Path work) throws IOException {
        // just checks it compiles and there is a proper nested beans handling ($ vs .)
        new Compiler(work, "NestedBeans", "LifecycledDep", "Bean2").compileAndAsserts((loader, container) -> {
            assertEquals(
                    List.of(
                            "test.p.Bean2",
                            "test.p.LifecycledDep",
                            "test.p.NestedBeans$Lifecycled",
                            "test.p.NestedBeans$MethodProducer",
                            "test.p.NestedBeans$MethodProducer$Produceable"),
                    container.getBeans().getBeans().values().stream()
                            .flatMap(Collection::stream)
                            .map(it -> it.type().getTypeName())
                            .filter(it -> it.startsWith("test.p."))
                            .sorted()
                            .toList());
        });
    }

    @Test
    void nestedJsonRpc(@TempDir final Path work) throws IOException {
        // just checks it compiles and there is a proper nested beans handling ($ vs .)
        new Compiler(work, "NestedJsonRpc").compileAndAsserts((loader, container) -> {
            assertEquals(
                    List.of(
                            "test.p.NestedJsonRpc$MyInput$FusionJsonCodec",
                            "test.p.NestedJsonRpc$MyResult$FusionJsonCodec",
                            "test.p.NestedJsonRpc$Rpc",
                            "test.p.NestedJsonRpc$Rpc$asynResult$FusionJsonRpcMethod"),
                    container.getBeans().getBeans().values().stream()
                            .flatMap(Collection::stream)
                            .map(it -> it.type().getTypeName())
                            .filter(it -> it.startsWith("test.p."))
                            .sorted()
                            .toList());
        });
    }

    @Test
    void applicationScopeIsLazy(@TempDir final Path work) throws IOException {
        new Compiler(work, "Lifecycled", "LifecycledDep").compileAndAsserts((loader, container) -> {
            final var instance = container.lookup(loader.apply("test.p.Lifecycled"));

            // before actually calling the instance (toString()) the real instance is null
            final var proxy = instance.instance();
            assertEquals("test.p.Lifecycled$FusionSubclass", proxy.getClass().getName());

            final Field realInstance;
            try {
                final var lookup = MethodHandles.lookup();
                final var privateLookupIn = MethodHandles.privateLookupIn(proxy.getClass(), lookup);

                final var handle = privateLookupIn
                        .findVarHandle(proxy.getClass(), "fusionContext", DelegatingContext.class)
                        .get(proxy);
                assertInstanceOf(SupplierDelegatingContext.class, handle);

                realInstance = instance.getClass().getDeclaredField("real");
                if (!realInstance.canAccess(instance)) {
                    realInstance.trySetAccessible();
                }
                assertNull(realInstance.get(instance));
            } catch (final NoSuchFieldException | IllegalAccessException e) {
                fail(e);
                return;
            }

            final var string = proxy.toString();
            assertEquals("init=1, destroyed=0, dep[init=1, destroyed=0]", string);
            try {
                final var realInstanceValue = realInstance.get(instance);
                assertNotNull(realInstance.get(instance));
                for (int i = 0; i < 3; i++) { // ensure we keep the same ref
                    instance.instance().toString();
                    assertSame(realInstanceValue, realInstance.get(instance));
                }
            } catch (final IllegalAccessException e) {
                fail(e);
            }
            instance.close();
        });
    }

    @Test
    void lifecycleAppScoped(@TempDir final Path work) throws IOException {
        final var ref = new AtomicReference<Instance<?>>();
        new Compiler(work, "Lifecycled", "LifecycledDep").compileAndAsserts((loader, container) -> {
            final var instance = container.lookup(loader.apply("test.p.Lifecycled"));
            ref.set(instance);
            assertEquals("init=1, destroyed=0, dep[init=1, destroyed=0]", instance.instance().toString());
            instance.close();
            // bean is app scoped so not destroyed there
            assertEquals("init=1, destroyed=0, dep[init=1, destroyed=0]", instance.instance().toString());
        });
        // here the bean is destroyed by the container
        assertEquals("init=1, destroyed=1, dep[init=1, destroyed=1]", ref.get().instance().toString());
    }

    @Test
    void emitting(@TempDir final Path work) throws IOException {
        new Compiler(work, "Emitting").compileAndAsserts((loader, container) -> {
            final var events = new ArrayList<String>();
            container.getListeners().doRegister(new FusionListener<String>() {
                @Override
                public Class<String> eventType() {
                    return String.class;
                }

                @Override
                public void onEvent(final RuntimeContainer container, final String event) {
                    assertNotNull(container);
                    events.add(event);
                }
            });
            final var instance = container.lookup(loader.apply("test.p.Emitting"));
            assertTrue(events.isEmpty());
            instance.instance().toString(); // triggers an emit
            assertEquals(1, events.size());
            instance.close();
            // bean is app scoped so not destroyed there
            assertEquals(List.of(">Emitting<"), events);
        });
    }

    @Test
    void listening(@TempDir final Path work) throws IOException {
        new Compiler(work, "Listening").compileAndAsserts((loader, container) -> {
            final var instance = container.lookup(loader.apply("test.p.Listening"));
            assertEquals("", instance.instance().toString());
            container.emit("hello");
            assertEquals("hello", instance.instance().toString());
            instance.close();
        });
    }

    @Test
    void listeningWithInjections(@TempDir final Path work) throws IOException {
        new Compiler(work, "ListeningWithInjections", "Bean2").compileAndAssertsInstance((container, instance) -> {
            assertEquals("", instance.instance().toString());
            container.emit("hello");
            assertEquals("hello, injections=bean2[]", instance.instance().toString());
        });
    }

    @Test
    void methodFactories(@TempDir final Path work) throws IOException {
        new Compiler(work, "MethodProducer", "Bean2").compileAndAsserts((loader, container) -> {
            final var factory = container.lookup(loader.apply("test.p.MethodProducer"));
            assertEquals("", factory.instance().toString());

            try (final var instance = container.lookup(Supplier.class)) {
                assertEquals("create", factory.instance().toString());

                assertEquals("bean2[]", instance.instance().get());
                assertEquals("create, create.get(bean2[])", factory.instance().toString());

                try (final var staticInstance = container.lookup(String.class)) {
                    assertEquals("<conf>", staticInstance.instance());
                    assertEquals("create, create.get(bean2[]), globalConf", factory.instance().toString());
                }
            }
            assertEquals("create, create.get(bean2[]), globalConf, create.close", factory.instance().toString());
        });
    }

    @Test
    void methodFactoryWithInjection(@TempDir final Path work) throws IOException {
        new Compiler(work, "MethodProducerWithInjections", "Bean2").compileAndAssertsInstance((container, i) -> {
            assertEquals("test.p.MethodProducerWithInjections$FusionSubclass", i.instance().getClass().getName());
            try (final var s = container.lookup(String.class)) {
                assertEquals(">bean2[]<", s.instance());
            }
        });
    }

    @Test
    void parameterizedTypeMethodFactories(@TempDir final Path work) throws IOException {
        new Compiler(work, "GenericProducer").compileAndAsserts((loader, container) -> {
            try (final var instance = container.lookup(List.class)) {
                assertEquals(List.of("generic", "conf"), instance.instance());
            }
        });
    }

    @Test
    void optionalInjection(@TempDir final Path work) throws IOException {
        new Compiler(work, "OptionalInjection", "NotABean").compileAndAsserts(
                instance -> assertEquals("bean1<Optional.empty>", instance.instance().toString()));

        // not: we reuse the same folder without any "cleanup" so we should at least use the same state than previous one + other stuff
        // (see state management in processor)
        new Compiler(work, "OptionalInjection", "NotABean", "NotABeanBeanImpl").compileAndAsserts(
                instance -> assertEquals("bean1<Optional[NotABeanBeanImpl[]]>", instance.instance().toString()));
    }

    @Test
    void inheritanceInjections(@TempDir final Path work) throws IOException {
        new Compiler(work, "Bean1Child", "Bean1", "Bean2").compileAndAsserts(
                instance -> assertEquals("bean1[bean2=<bean2[]>, bean22=<bean2[]>]", instance.instance().toString()));
    }

    @Test
    void constructorInjection(@TempDir final Path work) throws IOException {
        new Compiler(work, "ConstructorInjection", "Bean2", "Bean21", "Bean22").compileAndAsserts(
                instance -> assertEquals("constructor<bean2=bean2[],list=[bean21, bean22]>", instance.instance().toString()));
    }

    @Test
    void configuration(@TempDir final Path work) throws IOException {
        new Compiler(work, "RecordConfiguration", "NestedConf", "TestConf", "EnumType", "EnumTypeWithFields")
                .compileAndAssertsInstance((container, instance) -> {
                    assertEquals(
                            "RecordConfiguration[" +
                                    "name=test, " +
                                    "toggle=true, " +
                                    "age=123, " +
                                    "aLong=456, " +
                                    "number=7.89, " +
                                    "bigNumber=10.2, " +
                                    "nested=NestedConf[nestedValue=down, second=Nest2[value=5]], " +
                                    "nesteds=[NestedConf[nestedValue=down1, second=Nest2[value=0]], NestedConf[nestedValue=down2, second=Nest2[value=0]]], " +
                                    "list=[ab, cde, fgh], " +
                                    "type=ENUM_1, " +
                                    "intWithDefault=100, " +
                                    "strWithDefault=bump, " +
                                    "listStrWithDefault=[bump, bump2]" +
                                    "]",
                            instance.instance().toString());
                    try (final var otherInstance = container.lookup(instance.instance().getClass())) { // scope is respected
                        assertSame(otherInstance.instance(), instance.instance());
                    }

                    // doc
                    try (final var in = requireNonNull(instance.instance().getClass().getClassLoader()
                            .getResourceAsStream("META-INF/fusion/configuration/documentation.json"))) {
                        assertEquals("{\n" +
                                        "  \"version\": 1,\n" +
                                        "  \"classes\": {\n" +
                                        "    \"test.p.NestedConf\": [\n" +
                                        "      {\n" +
                                        "        \"name\": \"nestedValue\",\n" +
                                        "        \"javaName\": \"nestedValue\",\n" +
                                        "        \"documentation\": \"The nested main value.\",\n" +
                                        "        \"defaultValue\": null,\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"ref\": \"test.p.NestedConf.Nest2\",\n" +
                                        "        \"name\": \"second\",\n" +
                                        "        \"javaName\": \"second\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"required\": false\n" +
                                        "      }\n" +
                                        "    ],\n" +
                                        "    \"test.p.NestedConf.Nest2\": [\n" +
                                        "      {\n" +
                                        "        \"name\": \"value\",\n" +
                                        "        \"javaName\": \"value\",\n" +
                                        "        \"documentation\": \"Some int.\",\n" +
                                        "        \"defaultValue\": 0,\n" +
                                        "        \"required\": false\n" +
                                        "      }\n" +
                                        "    ],\n" +
                                        "    \"test.p.RecordConfiguration\": [\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.age\",\n" +
                                        "        \"javaName\": \"age\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": 0,\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.bigInt\",\n" +
                                        "        \"javaName\": \"aLong\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": 0,\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.bigNumber\",\n" +
                                        "        \"javaName\": \"bigNumber\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": null,\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.intWithDefault\",\n" +
                                        "        \"javaName\": \"intWithDefault\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": 100,\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.list\",\n" +
                                        "        \"javaName\": \"list\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": null,\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.listStrWithDefault\",\n" +
                                        "        \"javaName\": \"listStrWithDefault\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": \"java.util.List.of(\\\"bump\\\",\\\"bump2\\\")\",\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.name\",\n" +
                                        "        \"javaName\": \"name\",\n" +
                                        "        \"documentation\": \"The app name\",\n" +
                                        "        \"defaultValue\": null,\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"ref\": \"test.p.NestedConf\",\n" +
                                        "        \"name\": \"app.nested\",\n" +
                                        "        \"javaName\": \"nested\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"ref\": \"test.p.NestedConf\",\n" +
                                        "        \"name\": \"app.nesteds.$index\",\n" +
                                        "        \"javaName\": \"nesteds\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.number\",\n" +
                                        "        \"javaName\": \"number\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": 0,\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.strWithDefault\",\n" +
                                        "        \"javaName\": \"strWithDefault\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": \"\\\"bump\\\"\",\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.toggle\",\n" +
                                        "        \"javaName\": \"toggle\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": false,\n" +
                                        "        \"required\": false\n" +
                                        "      },\n" +
                                        "      {\n" +
                                        "        \"name\": \"app.type\",\n" +
                                        "        \"javaName\": \"type\",\n" +
                                        "        \"documentation\": \"\",\n" +
                                        "        \"defaultValue\": null,\n" +
                                        "        \"required\": false\n" +
                                        "      }\n" +
                                        "    ]\n" +
                                        "  },\n" +
                                        "  \"roots\": [\n" +
                                        "    \"test.p.RecordConfiguration\"\n" +
                                        "  ]\n" +
                                        "}",
                                simpleFormat(in));
                    } catch (final IOException e) {
                        fail(e);
                    }
                });
    }


    @Test
    void configurationMap(@TempDir final Path work) throws IOException {
        new Compiler(work, "MapConfiguration")
                .compileAndAsserts((loader, container) -> {
                    // handle the configuration with a custom config source
                    container.getBeans().doRegister(new BaseBean<ConfigurationSource>(ConfigurationSource.class, DefaultScoped.class, 0, Map.of()) {
                        @Override
                        public ConfigurationSource create(RuntimeContainer container, List<Instance<?>> dependents) {
                            return new ConfigurationSource() {
                                @Override
                                public String get(final String key) {
                                    return switch (key) {
                                        case "conf.keyValues" -> """
                                                # properties syntax
                                                a = 1
                                                """;
                                        default -> null;
                                    };
                                }
                            };
                        }
                    });
                    // clean the bean to use our custom config source
                    if (container.getContexts().findContext(ApplicationScoped.class).orElseThrow() instanceof  ApplicationFusionContext ac){
                        ac.clean(container.lookup(Configuration.class).bean());
                    }
                    if (container instanceof RuntimeContainerImpl r) {
                        r.clearCache();
                    }

                    try (final var instance = container.lookup(loader.apply("test.p.MapConfiguration"))) {
                        // model
                        assertEquals(
                                "MapConfiguration[keyValues={a=1}]",
                                instance.instance().toString());

                        // doc
                        try (final var in = requireNonNull(instance.instance().getClass().getClassLoader()
                                .getResourceAsStream("META-INF/fusion/configuration/documentation.json"))) {
                            assertEquals("""
                                            {
                                              "version": 1,
                                              "classes": {
                                                "test.p.MapConfiguration": [
                                                  {
                                                    "name": "conf.keyValues",
                                                    "javaName": "keyValues",
                                                    "documentation": "",
                                                    "defaultValue": null,
                                                    "required": false
                                                  }
                                                ]
                                              },
                                              "roots": [
                                                "test.p.MapConfiguration"
                                              ]
                                            }""",
                                    simpleFormat(in));
                        } catch (final IOException e) {
                            fail(e);
                        }
                    }
                });
    }

    @Test
    void requiredConfigurationParam(@TempDir final Path work) throws IOException {
        final var clazz = "configuration.ConfigurationMissingParamMessage";
        final var compiler = new Compiler(work, clazz);
        compiler.compileAndAsserts((loader, container) ->
                assertEquals("No value for 'conf.name'", assertThrows(
                        IllegalArgumentException.class, () -> container.lookup(loader.apply("test.p." + clazz)))
                        .getMessage()));
        compiler.asserts(
                (loader, container) -> assertEquals("No value for 'conf.nested.lower'", assertThrows(
                        IllegalArgumentException.class, () -> container.lookup(loader.apply("test.p." + clazz)))
                        .getMessage()),
                new ProvidedInstanceBean<>(DefaultScoped.class, Configuration.class, () -> k -> switch (k) {
                    case "conf.name" -> of("set");
                    default -> empty();
                }) {
                    @Override
                    public int priority() {
                        return super.priority() + 1;
                    }
                });
    }

    @Test
    void respectNullDefault(@TempDir final Path work) throws IOException {
        final var clazz = "configuration.ConfigurationWithNullNested";
        new Compiler(work, clazz).compileAndAsserts((loader, container) -> {
            try (final var i = container.lookup(loader.apply("test.p." + clazz))) {
                assertEquals("ConfigurationWithNullNested[nested=null, int64=null, nesteds=null, values=null]", i.instance().toString());
            }
        });
    }

    @Test
    @Disabled("private injections not yet implemented, requires hacks to annotation processors lifecycle which are not great framework wise (see lombok for an ex)")
    void privateInjection(@TempDir final Path work) throws IOException {
        new Compiler(work, "Bean1Private", "Bean2").compileAndAsserts(instance -> assertEquals("bean1[bean2[]]", instance.instance().toString()));
    }

    @Test
    void jsonEmptyRecord(@TempDir final Path work) throws IOException {
        new Compiler(work, "EmptyRecord").jsonRoundTripAsserts("test.p.EmptyRecord", "{}", "EmptyRecord[]");
    }

    @Test
    void jsonStringRecord(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonRecords")
                .jsonRoundTripAsserts("test.p.JsonRecords$StringHolder", "{\"name\":\"hello\"}", "StringHolder[name=hello]");
    }

    @Test
    void jsonComplexRecord(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonRecords")
                .jsonRoundTripAsserts("test.p.JsonRecords$AllInOne",
                        "" +
                                "{" +
                                "\"bigNumber\":\"1E+10\",\"bigNumbers\":[\"123\",\"456\"],\"booleanList\":[true,false]," +
                                "\"date\":\"2022-12-06\",\"dateList\":[\"2022-12-06\",\"2022-12-07\"]," +
                                "\"dateTime\":\"2022-12-06T14:47\",\"dateTimeList\":[\"2022-12-06T15:19\",\"2022-12-06T15:19:49\"]," +
                                "\"doubleList\":[9.1,10.2]," +
                                "\"generic\":{\"gen\":true},\"genericList\":[{\"gen\":{\"n\":true}},{\"gen2\":{\"other\":2}}]," +
                                "\"intList\":[3,4],\"lg\":3,\"longList\":[5,6],\"mapNested\":{\"k\":{\"name\":\"self\"}}," +
                                "\"mapStringInt\":{\"k\":1},\"mapStringString\":{\"k\":\"v\"},\"more\":4.5,\"nested\":{\"name\":\"lower\"}," +
                                "\"nestedList\":[{\"name\":\"santa\"},{\"name\":\"nicolas\"}],\"nullableInt\":2,\"offset\":\"2022-12-06T14:47Z\"," +
                                "\"offsetList\":[\"2022-12-06T15:19Z\",\"2022-12-06T15:19:49Z\"],\"simplest\":\"the chars\"," +
                                "\"stringList\":[\"first\",\"second\"],\"zoned\":\"2022-12-06T14:47Z\"," +
                                "\"zonedList\":[\"2022-12-06T15:19Z\",\"2022-12-06T15:19:49Z\"]," +
                                // @JsonOthers - pushed at the end by default sorting (serialization)
                                "\"fall\":\"back\",\"fall-obj\":{\"down\":1},\"fall-list\":[5],\"unmapped\":true," +
                                // custom order
                                "\"aBool\":true,\"integer\":1}",
                        "AllInOne[" +
                                "aBool=true, bigDecimal=1E+10, integer=1, nullableInt=2, lg=3, more=4.5, simplest=the chars, " +
                                "date=2022-12-06, dateTime=2022-12-06T14:47, offset=2022-12-06T14:47Z, zoned=2022-12-06T14:47Z, " +
                                "generic={gen=true}, nested=StringHolder[name=lower], " +
                                "booleanList=[true, false], bigDecimalList=[123, 456], intList=[3, 4], longList=[5, 6], doubleList=[9.1, 10.2], " +
                                "stringList=[first, second], dateList=[2022-12-06, 2022-12-07], " +
                                "dateTimeList=[2022-12-06T15:19, 2022-12-06T15:19:49], offsetList=[2022-12-06T15:19Z, 2022-12-06T15:19:49Z], " +
                                "zonedList=[2022-12-06T15:19Z, 2022-12-06T15:19:49Z], genericList=[{gen={n=true}}, {gen2={other=2}}], " +
                                "nestedList=[StringHolder[name=santa], StringHolder[name=nicolas]], " +
                                "mapStringString={k=v}, mapStringInt={k=1}, mapNested={k=StringHolder[name=self]}, " +
                                "others={fall=back, fall-obj={down=1}, fall-list=[5], unmapped=true}]",
                        (loader, mapper) -> { // ensure JSON-Schemas are generated
                            try (final var in = requireNonNull(Thread.currentThread().getContextClassLoader()
                                    .getResourceAsStream("META-INF/fusion/json/schemas.json"))) {
                                assertEquals("""
                                                {
                                                  "schemas": {
                                                    "test.p.JsonRecords.AllInOne": {
                                                      "title": "AllInOne",
                                                      "type": "object",
                                                      "properties": {
                                                        "aBool": {
                                                          "nullable": false,
                                                          "type": "boolean"
                                                        },
                                                        "bigNumber": {
                                                          "nullable": true,
                                                          "type": "string"
                                                        },
                                                        "bigNumbers": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "type": "string"
                                                          }
                                                        },
                                                        "booleanList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "type": "boolean"
                                                          }
                                                        },
                                                        "date": {
                                                          "nullable": true,
                                                          "format": "date",
                                                          "type": "string"
                                                        },
                                                        "dateList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "format": "date",
                                                            "type": "string"
                                                          }
                                                        },
                                                        "dateTime": {
                                                          "nullable": true,
                                                          "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?$",
                                                          "type": "string"
                                                        },
                                                        "dateTimeList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?$",
                                                            "type": "string"
                                                          }
                                                        },
                                                        "doubleList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "type": "integer",
                                                            "$id": "number"
                                                          }
                                                        },
                                                        "generic": {
                                                          "nullable": true,
                                                          "additionalProperties": true,
                                                          "type": "object"
                                                        },
                                                        "genericList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "additionalProperties": true,
                                                            "type": "object"
                                                          }
                                                        },
                                                        "intList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "format": "int32",
                                                            "type": "integer"
                                                          }
                                                        },
                                                        "integer": {
                                                          "nullable": false,
                                                          "format": "int32",
                                                          "type": "integer"
                                                        },
                                                        "lg": {
                                                          "nullable": false,
                                                          "format": "int64",
                                                          "type": "integer",
                                                          "$id": "number"
                                                        },
                                                        "longList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "format": "int64",
                                                            "type": "integer",
                                                            "$id": "number"
                                                          }
                                                        },
                                                        "mapNested": {
                                                          "nullable": true,
                                                          "additionalProperties": {
                                                            "nullable": true,
                                                            "$ref": "#/schemas/test.p.JsonRecords.StringHolder"
                                                          },
                                                          "type": "object"
                                                        },
                                                        "mapStringInt": {
                                                          "nullable": true,
                                                          "additionalProperties": {
                                                            "nullable": true,
                                                            "format": "int32",
                                                            "type": "integer"
                                                          },
                                                          "type": "object"
                                                        },
                                                        "mapStringString": {
                                                          "nullable": true,
                                                          "additionalProperties": {
                                                            "nullable": true,
                                                            "type": "string"
                                                          },
                                                          "type": "object"
                                                        },
                                                        "more": {
                                                          "nullable": false,
                                                          "type": "integer",
                                                          "$id": "number"
                                                        },
                                                        "nested": {
                                                          "nullable": true,
                                                          "$ref": "#/schemas/test.p.JsonRecords.StringHolder"
                                                        },
                                                        "nestedList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "$ref": "#/schemas/test.p.JsonRecords.StringHolder"
                                                          }
                                                        },
                                                        "nullableInt": {
                                                          "nullable": true,
                                                          "format": "int32",
                                                          "type": "integer"
                                                        },
                                                        "offset": {
                                                          "nullable": true,
                                                          "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$",
                                                          "type": "string"
                                                        },
                                                        "offsetList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$",
                                                            "type": "string"
                                                          }
                                                        },
                                                        "others": {
                                                          "nullable": true,
                                                          "additionalProperties": {
                                                            "nullable": true,
                                                            "additionalProperties": true,
                                                            "type": "object"
                                                          },
                                                          "type": "object"
                                                        },
                                                        "simplest": {
                                                          "nullable": true,
                                                          "type": "string"
                                                        },
                                                        "stringList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "type": "string"
                                                          }
                                                        },
                                                        "zoned": {
                                                          "nullable": true,
                                                          "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\\\[.*\\\\])?$",
                                                          "type": "string"
                                                        },
                                                        "zonedList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\\\[.*\\\\])?$",
                                                            "type": "string"
                                                          }
                                                        }
                                                      },
                                                      "$id": "test.p.JsonRecords.AllInOne"
                                                    },
                                                    "test.p.JsonRecords.OthersOnly": {
                                                      "title": "OthersOnly",
                                                      "type": "object",
                                                      "properties": {
                                                        "others": {
                                                          "nullable": true,
                                                          "additionalProperties": {
                                                            "nullable": true,
                                                            "additionalProperties": true,
                                                            "type": "object"
                                                          },
                                                          "type": "object"
                                                        }
                                                      },
                                                      "$id": "test.p.JsonRecords.OthersOnly"
                                                    },
                                                    "test.p.JsonRecords.StringHolder": {
                                                      "title": "StringHolder",
                                                      "type": "object",
                                                      "properties": {
                                                        "name": {
                                                          "nullable": true,
                                                          "type": "string"
                                                        }
                                                      },
                                                      "$id": "test.p.JsonRecords.StringHolder"
                                                    },
                                                    "test.p.JsonRecords.StrongTyping": {
                                                      "title": "StrongTyping",
                                                      "type": "object",
                                                      "properties": {
                                                        "aBool": {
                                                          "nullable": false,
                                                          "type": "boolean"
                                                        },
                                                        "bigNumber": {
                                                          "nullable": true,
                                                          "type": "string"
                                                        },
                                                        "bigNumbers": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "type": "string"
                                                          }
                                                        },
                                                        "booleanList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "type": "boolean"
                                                          }
                                                        },
                                                        "date": {
                                                          "nullable": true,
                                                          "format": "date",
                                                          "type": "string"
                                                        },
                                                        "dateList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "format": "date",
                                                            "type": "string"
                                                          }
                                                        },
                                                        "dateTime": {
                                                          "nullable": true,
                                                          "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?$",
                                                          "type": "string"
                                                        },
                                                        "dateTimeList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?$",
                                                            "type": "string"
                                                          }
                                                        },
                                                        "doubleList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "type": "integer",
                                                            "$id": "number"
                                                          }
                                                        },
                                                        "generic": {
                                                          "nullable": true,
                                                          "additionalProperties": true,
                                                          "type": "object"
                                                        },
                                                        "genericList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "additionalProperties": true,
                                                            "type": "object"
                                                          }
                                                        },
                                                        "intList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "format": "int32",
                                                            "type": "integer"
                                                          }
                                                        },
                                                        "integer": {
                                                          "nullable": false,
                                                          "format": "int32",
                                                          "type": "integer"
                                                        },
                                                        "lg": {
                                                          "nullable": false,
                                                          "format": "int64",
                                                          "type": "integer",
                                                          "$id": "number"
                                                        },
                                                        "longList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "format": "int64",
                                                            "type": "integer",
                                                            "$id": "number"
                                                          }
                                                        },
                                                        "mapNested": {
                                                          "nullable": true,
                                                          "additionalProperties": {
                                                            "nullable": true,
                                                            "$ref": "#/schemas/test.p.JsonRecords.StringHolder"
                                                          },
                                                          "type": "object"
                                                        },
                                                        "mapStringInt": {
                                                          "nullable": true,
                                                          "additionalProperties": {
                                                            "nullable": true,
                                                            "format": "int32",
                                                            "type": "integer"
                                                          },
                                                          "type": "object"
                                                        },
                                                        "mapStringString": {
                                                          "nullable": true,
                                                          "additionalProperties": {
                                                            "nullable": true,
                                                            "type": "string"
                                                          },
                                                          "type": "object"
                                                        },
                                                        "more": {
                                                          "nullable": false,
                                                          "type": "integer",
                                                          "$id": "number"
                                                        },
                                                        "nested": {
                                                          "nullable": true,
                                                          "$ref": "#/schemas/test.p.JsonRecords.StringHolder"
                                                        },
                                                        "nestedList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "$ref": "#/schemas/test.p.JsonRecords.StringHolder"
                                                          }
                                                        },
                                                        "nullableInt": {
                                                          "nullable": true,
                                                          "format": "int32",
                                                          "type": "integer"
                                                        },
                                                        "offset": {
                                                          "nullable": true,
                                                          "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$",
                                                          "type": "string"
                                                        },
                                                        "offsetList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$",
                                                            "type": "string"
                                                          }
                                                        },
                                                        "simplest": {
                                                          "nullable": true,
                                                          "type": "string"
                                                        },
                                                        "stringList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "type": "string"
                                                          }
                                                        },
                                                        "zoned": {
                                                          "nullable": true,
                                                          "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\\\[.*\\\\])?$",
                                                          "type": "string"
                                                        },
                                                        "zonedList": {
                                                          "type": "array",
                                                          "items": {
                                                            "nullable": true,
                                                            "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\\\[.*\\\\])?$",
                                                            "type": "string"
                                                          }
                                                        }
                                                      },
                                                      "$id": "test.p.JsonRecords.StrongTyping"
                                                    }
                                                  }
                                                }""",
                                        new SimplePrettyFormatter(new JsonMapperImpl(List.of(new ObjectJsonCodec()), c -> empty()))
                                                .apply(new String(in.readAllBytes(), UTF_8)));
                            } catch (final IOException e) {
                                fail(e);
                            }
                        });
    }

    @Test
    void crd(@TempDir final Path work) throws IOException {
        new Compiler(work, "crd.MyOperator")
                .compileAndAsserts(
                        (loader, mapper) -> { // ensure JSON-Schemas are generated
                            try (final var in = requireNonNull(Thread.currentThread().getContextClassLoader()
                                    .getResourceAsStream("META-INF/fusion/kubernetes/crd/test.yupiik.io/v1/MyCrd.json"))) {
                                assertEquals("""
                                                {
                                                  "apiVersion": "apiextensions.k8s.io/v1",
                                                  "kind": "CustomResourceDefinition",
                                                  "metadata": {
                                                    "name": "mycrds.test.yupiik.io"
                                                  },
                                                  "spec": {
                                                    "group": "test.yupiik.io",
                                                    "names": {
                                                      "kind": "MyCrd",
                                                      "plural": "mycrds",
                                                      "shortNames": [
                                                        "mcrd"
                                                      ],
                                                      "singular": "mycrd"
                                                    },
                                                    "scope": "Namespaced",
                                                    "versions": [
                                                      {
                                                        "additionalPrinterColumns": [
                                                          {
                                                            "jsonPath": ".spec.type",
                                                            "name": "Type",
                                                            "type": "string"
                                                          }
                                                        ],
                                                        "name": "v1",
                                                        "schema": {
                                                          "openAPIV3Schema": {
                                                            "spec": {
                                                              "title": "MySpec",
                                                              "type": "object",
                                                              "properties": {
                                                                "count": {
                                                                  "nullable": false,
                                                                  "format": "int32",
                                                                  "type": "integer"
                                                                },
                                                                "nested": {
                                                                  "nullable": true,
                                                                  "title": "MyNestedSpec",
                                                                  "type": "object",
                                                                  "properties": {
                                                                    "type": {
                                                                      "nullable": true,
                                                                      "type": "string"
                                                                    },
                                                                    "values": {
                                                                      "type": "array",
                                                                      "items": {
                                                                        "nullable": true,
                                                                        "type": "string"
                                                                      }
                                                                    }
                                                                  }
                                                                }
                                                              }
                                                            },
                                                            "status": {
                                                              "title": "MyStatus",
                                                              "type": "object",
                                                              "properties": {
                                                                "state": {
                                                                  "nullable": true,
                                                                  "type": "string"
                                                                }
                                                              }
                                                            }
                                                          },
                                                          "subresources": {
                                                            "status": {}
                                                          }
                                                        },
                                                        "selectableFields": [
                                                          {
                                                            "jsonPath": ".spec.type"
                                                          }
                                                        ],
                                                        "served": true,
                                                        "storage": true
                                                      }
                                                    ]
                                                  }
                                                }""",
                                        new SimplePrettyFormatter(new JsonMapperImpl(List.of(new ObjectJsonCodec()), c -> empty()))
                                                .apply(new String(in.readAllBytes(), UTF_8)));
                            } catch (final IOException e) {
                                fail(e);
                            }
                        });
    }

    @Test
    void jsonObjectMapping(@TempDir final Path work) throws IOException {
        new Compiler(work, "json.GenericlyTyped").compileAndAsserts((loader, container) -> {
            final var recordType = loader.apply("test.p.json.GenericlyTyped");
            try {
                final var constructor = recordType.getConstructor(String.class, Object.class);
                try (final var jsonMapperInstance = container.lookup(JsonMapper.class)) {
                    final var mapper = jsonMapperInstance.instance();

                    // test instances, they are records or jvm types so we can use equals
                    final var number = constructor.newInstance("number", BigDecimal.ONE);
                    final var string = constructor.newInstance("string", "hello");
                    final var bool = constructor.newInstance("boolean", true);
                    final var object = constructor.newInstance("object", Map.of("foo", "bar"));
                    final var array = constructor.newInstance("array", List.of("foo", "bar"));

                    // serialization
                    assertEquals("{\"name\":\"number\",\"value\":1}", mapper.toString(number));
                    assertEquals("{\"name\":\"string\",\"value\":\"hello\"}", mapper.toString(string));
                    assertEquals("{\"name\":\"boolean\",\"value\":true}", mapper.toString(bool));
                    assertEquals("{\"name\":\"object\",\"value\":{\"foo\":\"bar\"}}", mapper.toString(object));
                    assertEquals("{\"name\":\"array\",\"value\":[\"foo\",\"bar\"]}", mapper.toString(array));

                    // deserialization
                    assertEquals(number, mapper.fromString(recordType, "{\"name\":\"number\",\"value\":1}"));
                    assertEquals(string, mapper.fromString(recordType, "{\"name\":\"string\",\"value\":\"hello\"}"));
                    assertEquals(bool, mapper.fromString(recordType, "{\"name\":\"boolean\",\"value\":true}"));
                    assertEquals(object, mapper.fromString(recordType, "{\"name\":\"object\",\"value\":{\"foo\":\"bar\"}}"));
                    assertEquals(array, mapper.fromString(recordType, "{\"name\":\"array\",\"value\":[\"foo\",\"bar\"]}"));
                }
            } catch (final Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void jsonMapListModel(@TempDir final Path work) throws IOException {
        new Compiler(work, "json.MapListModel", "json.Foo").compileAndAsserts((loader, container) -> {
            final var recordType = loader.apply("test.p.json.MapListModel");
            final var fooType = loader.apply("test.p.json.Foo");
            try {
                final var constructor = recordType.getConstructor(Map.class);
                final var fooConstructor = fooType.getConstructor(int.class);
                try (final var jsonMapperInstance = container.lookup(JsonMapper.class)) {
                    final var mapper = jsonMapperInstance.instance();
                    final var sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                    sorted.putAll(Map.of(
                            "a", List.of(fooConstructor.newInstance(1)),
                            "b", List.of(fooConstructor.newInstance(2), fooConstructor.newInstance(3))));
                    final var instance = constructor.newInstance(sorted);
                    assertEquals("{\"items\":{\"a\":[{\"value\":1}],\"b\":[{\"value\":2},{\"value\":3}]}}", mapper.toString(instance));
                    assertEquals(instance, mapper.fromString(recordType, "{\"items\":{\"a\":[{\"value\":1}],\"b\":[{\"value\":2},{\"value\":3}]}}"));
                }
            } catch (final Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void listMap(@TempDir final Path work) throws IOException {
        new Compiler(work, "json.ListMap").compileAndAsserts((loader, container) -> {
            final var recordType = loader.apply("test.p.json.ListMap");
            try {
                final var constructor = recordType.getConstructor(List.class);
                try (final var jsonMapperInstance = container.lookup(JsonMapper.class)) {
                    final var mapper = jsonMapperInstance.instance();
                    final var sorted = List.of(Map.of("a", BigDecimal.ONE));
                    final var instance = constructor.newInstance(sorted);
                    assertEquals("{\"items\":[{\"a\":1}]}", mapper.toString(instance));
                    assertEquals(instance, mapper.fromString(recordType, "{\"items\":[{\"a\":1}]}"));
                }
            } catch (final Exception e) {
                fail(e);
            }
        });
    }

    @Test
    void jsonComplexNoJsonOthers(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonRecords").jsonRoundTripAsserts("test.p.JsonRecords$StrongTyping", "" +
                        "{" +
                        "\"aBool\":true,\"bigNumber\":\"1E+10\",\"bigNumbers\":[\"123\",\"456\"],\"booleanList\":[true,false]," +
                        "\"date\":\"2022-12-06\",\"dateList\":[\"2022-12-06\",\"2022-12-07\"]," +
                        "\"dateTime\":\"2022-12-06T14:47\",\"dateTimeList\":[\"2022-12-06T15:19\",\"2022-12-06T15:19:49\"]," +
                        "\"doubleList\":[9.1,10.2]," +
                        "\"generic\":{\"gen\":true},\"genericList\":[{\"gen\":{\"n\":true}},{\"gen2\":{\"other\":2}}]," +
                        "\"intList\":[3,4],\"integer\":1,\"lg\":3,\"longList\":[5,6],\"mapNested\":{\"k\":{\"name\":\"self\"}}," +
                        "\"mapStringInt\":{\"k\":1},\"mapStringString\":{\"k\":\"v\"},\"more\":4.5,\"nested\":{\"name\":\"lower\"}," +
                        "\"nestedList\":[{\"name\":\"santa\"},{\"name\":\"nicolas\"}],\"nullableInt\":2,\"offset\":\"2022-12-06T14:47Z\"," +
                        "\"offsetList\":[\"2022-12-06T15:19Z\",\"2022-12-06T15:19:49Z\"],\"simplest\":\"the chars\"," +
                        "\"stringList\":[\"first\",\"second\"],\"zoned\":\"2022-12-06T14:47Z\"," +
                        "\"zonedList\":[\"2022-12-06T15:19Z\",\"2022-12-06T15:19:49Z\"]}",
                "StrongTyping[" +
                        "aBool=true, bigDecimal=1E+10, integer=1, nullableInt=2, lg=3, more=4.5, simplest=the chars, " +
                        "date=2022-12-06, dateTime=2022-12-06T14:47, offset=2022-12-06T14:47Z, zoned=2022-12-06T14:47Z, " +
                        "generic={gen=true}, nested=StringHolder[name=lower], " +
                        "booleanList=[true, false], bigDecimalList=[123, 456], intList=[3, 4], longList=[5, 6], doubleList=[9.1, 10.2], " +
                        "stringList=[first, second], dateList=[2022-12-06, 2022-12-07], " +
                        "dateTimeList=[2022-12-06T15:19, 2022-12-06T15:19:49], offsetList=[2022-12-06T15:19Z, 2022-12-06T15:19:49Z], " +
                        "zonedList=[2022-12-06T15:19Z, 2022-12-06T15:19:49Z], genericList=[{gen={n=true}}, {gen2={other=2}}], " +
                        "nestedList=[StringHolder[name=santa], StringHolder[name=nicolas]], " +
                        "mapStringString={k=v}, mapStringInt={k=1}, mapNested={k=StringHolder[name=self]}]");
    }

    @Test
    void jsonWithNull(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonRecords").compileAndJsonAsserts((loader, mapper) -> {
            final var model = loader.apply("test.p.JsonRecords$StrongTyping");
            final Object instance;
            try (final var reader = new StringReader("{}")) {
                instance = mapper.read(model, reader);
                assertNotNull(instance);
            }

            assertEquals("StrongTyping[aBool=false, bigDecimal=null, integer=0, nullableInt=null, lg=0, more=0.0, simplest=null, date=null, dateTime=null, offset=null, zoned=null, generic=null, nested=null, booleanList=null, bigDecimalList=null, intList=null, longList=null, doubleList=null, stringList=null, dateList=null, dateTimeList=null, offsetList=null, zonedList=null, genericList=null, nestedList=null, mapStringString=null, mapStringInt=null, mapNested=null]", instance.toString());

            final var writer = new StringWriter();
            try (writer;
                 final JsonMapper withNulls = mapper.as(JsonMapper.Configuring.class).orElseThrow().serializeNulls().build()) {
                withNulls.write(instance, writer);
            } catch (final IOException e) {
                fail(e);
            }

            final var json = writer.toString();
            assertEquals("{\"aBool\":false,\"bigNumber\":null,\"bigNumbers\":null,\"booleanList\":null,\"date\":null,\"dateList\":null,\"dateTime\":null,\"dateTimeList\":null,\"doubleList\":null,\"generic\":null,\"genericList\":null,\"intList\":null,\"integer\":0,\"lg\":0,\"longList\":null,\"mapNested\":null,\"mapStringInt\":null,\"mapStringString\":null,\"more\":0.0,\"nested\":null,\"nestedList\":null,\"nullableInt\":null,\"offset\":null,\"offsetList\":null,\"simplest\":null,\"stringList\":null,\"zoned\":null,\"zonedList\":null}", json);
            mapper.fromString(Object.class, json); // just check the syntax
        });
    }

    @Test
    void jsonEnum(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonEnumCustomMapping")
                .jsonRoundTripAsserts(
                        "test.p.JsonEnumCustomMapping$Model",
                        "{\"enumValue\":\"second\",\"enumValues\":[\"first\",\"second\"]}",
                        "Model[enumValue=B, enumValues=[A, B]]");
    }

    @Test
    void jsonModelLoop(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonCycle")
                .jsonRoundTripAsserts("test.p.JsonCycle",
                        "{\"name\":\"child\",\"parent\":{\"name\":\"root\"}}",
                        "JsonCycle[parent=JsonCycle[parent=null, name=root], name=child]",
                        (loader, mapper) -> { // ensure JSON-Schemas are generated even when there is a loop
                            try (final var in = requireNonNull(Thread.currentThread().getContextClassLoader()
                                    .getResourceAsStream("META-INF/fusion/json/schemas.json"))) {
                                assertEquals("{\"schemas\":{\"test.p.JsonCycle\":{\"title\":\"JsonCycle\",\"type\":\"object\",\"properties\":" +
                                                "{\"name\":{\"nullable\":true,\"type\":\"string\"}," +
                                                "\"parent\":{\"nullable\":true,\"$ref\":\"#/schemas/test.p.JsonCycle\"}},\"$id\":\"test.p.JsonCycle\"}}}",
                                        new String(in.readAllBytes(), UTF_8));
                            } catch (final IOException e) {
                                fail(e);
                            }
                        });
    }

    @Test
    void invalidJsonOthers(@TempDir final Path work) {
        new Compiler(work, "InvalidJsonOthers").assertCompiles(1);
    }

    @Test
    @Disabled("not yet supported")
    void incrementalCompilation(@TempDir final Path work) throws IOException {
        final var compiler = new Compiler(work, "Bean1", "Bean2");
        compiler.compileAndAsserts((loader, container) -> assertEquals(6, container.getBeans().getBeans().size()));

        // reuse the same dir and build something new, ensure we don't loose beans
        new Compiler(work, "Bean21")
                .compileAndAsserts((loader, container) -> assertEquals(7, container.getBeans().getBeans().size()));
    }

    @TestFactory
    Stream<DynamicTest> jsonRpc(@TempDir final Path work) throws IOException {
        final var compiler = new Compiler(work, "JsonRpcEndpoints").assertCompiles(0);
        final var loader = new CompilationClassLoader(compiler.getClasses());
        final var container = ConfiguringContainer.of().start();
        final var endpointInstance = container.lookup(JsonRpcEndpoint.class);
        final var instance = endpointInstance.instance();

        final var lockForConcurrency = new Semaphore(1); // cause we run the test suite in parallel
        final var counter = new AtomicInteger(12);
        final Runnable release = () -> {
            endpointInstance.close();
            container.close();
            loader.close();
        };
        return Stream.of(
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_generatedMethods", () -> assertEquals(
                        List.of(
                                "test.p.JsonRpcEndpoints$arg$FusionJsonRpcMethod",
                                "test.p.JsonRpcEndpoints$asynResult$FusionJsonRpcMethod",
                                "test.p.JsonRpcEndpoints$fail$FusionJsonRpcMethod",
                                "test.p.JsonRpcEndpoints$offsetDateTime$FusionJsonRpcMethod",
                                "test.p.JsonRpcEndpoints$paramTypes$FusionJsonRpcMethod",
                                "test.p.JsonRpcEndpoints$req$FusionJsonRpcMethod",
                                "test.p.JsonRpcEndpoints$result$FusionJsonRpcMethod"),
                        container.getBeans().getBeans().keySet().stream()
                                .filter(Class.class::isInstance)
                                .map(Class.class::cast)
                                .map(Class::getName)
                                .filter(it -> it.endsWith("$FusionJsonRpcMethod"))
                                .sorted()
                                .toList()), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_offsetDateTime", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"offsetDateTime\",\"params\":{\"date\":\"2023-07-31T15:06:37Z\"}}",
                        "{\"jsonrpc\":\"2.0\",\"result\":{\"name\":\"2023-07-31T15:06:37Z\"}}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_unknownMethod", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"unknown\"}",
                        "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32601,\"message\":\"Unknown method (unknown)\"}}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_sync_outputOnly", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"test1\"}",
                        "{\"jsonrpc\":\"2.0\",\"result\":{\"name\":\"test1\"}}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_async_outputOnly", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"test2\"}",
                        "{\"jsonrpc\":\"2.0\",\"result\":{\"name\":\"test2\"}}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_singleParam", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"arg\",\"params\":{\"wrapper\":{\"name\":\"noisuf\"}}}",
                        "{\"jsonrpc\":\"2.0\",\"result\":{\"name\":\"fusion\"}}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_singleParam+request", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"req\",\"params\":{\"input\":{\"name\":\"fusion\"}}}",
                        "{\"jsonrpc\":\"2.0\",\"result\":{\"name\":\"fusion (/jsonrpc)\"}}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_sync_failure", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"fail\",\"params\":{\"direct\":true}}",
                        "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-2,\"message\":\"oops for test\"}}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_async_failure", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"fail\"}",
                        "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-2,\"message\":\"oops for test [promise]\"}}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_paramTypes_defaults", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"paramTypes\",\"params\":{}}",
                        "{\"jsonrpc\":\"2.0\",\"result\":\"" +
                                "null\\n" +
                                "false\\n" +
                                "null\\n" +
                                "0\\n" +
                                "null\\n" +
                                "0\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\\n" +
                                "null\"}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_paramTypes", () -> assertJsonRpc(instance,
                        "{\"jsonrpc\":\"2.0\",\"method\":\"paramTypes\",\"params\":{" +
                                "\"object\": {\"whatever\":\"works\"}," +
                                "\"bool\": true," +
                                "\"boolWrapper\": true," +
                                "\"integer\": 1," +
                                "\"intWrapper\": 2," +
                                "\"longNumber\": 3," +
                                "\"longWrapper\": 4," +
                                "\"string\": \"something\"," +
                                "\"model\": {\"name\":\"fusion\"}," +
                                "\"objectList\": [{\"idx\":1},{\"idx\":2}]," +
                                "\"boolWrapperList\": [true, false]," +
                                "\"intWrapperList\": [10, 20]," +
                                "\"longWrapperList\": [30, 40]," +
                                "\"stringList\": [\"simple\", \"hard\"]," +
                                "\"modelList\": [ {\"name\":\"fusion in list\"} ]," +
                                "\"objectMap\": {\"k1\":{\"obj\":true}}," +
                                "\"boolWrapperMap\": {\"kb\":true}," +
                                "\"intWrapperMap\":{\"ki\":100}," +
                                "\"longWrapperMap\":{\"kl\":200}," +
                                "\"stringMap\":{\"ks\":\"val\"}," +
                                "\"modelMap\":{\"km\": {\"name\":\"fusion in map\"}}" +
                                "}}",
                        "{\"jsonrpc\":\"2.0\",\"result\":\"" +
                                "{whatever=works}\\n" +
                                "true\\n" +
                                "true\\n" +
                                "1\\n" +
                                "2\\n" +
                                "3\\n" +
                                "4\\n" +
                                "something\\n" +
                                "MyInput[name=fusion]\\n" +
                                "[{idx=1}, {idx=2}]\\n" +
                                "[true, false]\\n" +
                                "[10, 20]\\n" +
                                "[30, 40]\\n" +
                                "[simple, hard]\\n" +
                                "[MyInput[name=fusion in list]]\\n" +
                                "{k1={obj=true}}\\n" +
                                "{kb=true}\\n" +
                                "{ki=100}\\n" +
                                "{kl=200}\\n" +
                                "{ks=val}\\n" +
                                "{km=MyInput[name=fusion in map]}" +
                                "\"}"), release),
                lockedDynamicTest(lockForConcurrency, counter, "jsonRpc_openrpc", () -> {
                    try (final var in = requireNonNull(Files.newInputStream(compiler.getClasses().resolve("META-INF/fusion/jsonrpc/openrpc.json")))) {
                        assertEquals("""
                                        {
                                          "schemas": {
                                            "test.p.JsonRpcEndpoints.MyInput": {
                                              "title": "MyInput",
                                              "type": "object",
                                              "properties": {
                                                "name": {
                                                  "nullable": true,
                                                  "type": "string"
                                                }
                                              },
                                              "$id": "test.p.JsonRpcEndpoints.MyInput"
                                            },
                                            "test.p.JsonRpcEndpoints.MyResult": {
                                              "title": "MyResult",
                                              "type": "object",
                                              "properties": {
                                                "name": {
                                                  "nullable": true,
                                                  "type": "string"
                                                }
                                              },
                                              "$id": "test.p.JsonRpcEndpoints.MyResult"
                                            }
                                          },
                                          "methods": {
                                            "arg": {
                                              "description": "",
                                              "errors": [],
                                              "name": "arg",
                                              "paramStructure": "either",
                                              "params": [
                                                {
                                                  "name": "wrapper",
                                                  "schema": {
                                                    "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyInput"
                                                  }
                                                }
                                              ],
                                              "result": {
                                                "name": "result",
                                                "schema": {
                                                  "nullable": true,
                                                  "additionalProperties": {
                                                    "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyResult"
                                                  },
                                                  "type": "object"
                                                }
                                              },
                                              "summary": ""
                                            },
                                            "fail": {
                                              "description": "",
                                              "errors": [],
                                              "name": "fail",
                                              "paramStructure": "either",
                                              "params": [
                                                {
                                                  "name": "direct",
                                                  "schema": {
                                                    "nullable": false,
                                                    "type": "boolean"
                                                  }
                                                }
                                              ],
                                              "result": {
                                                "name": "result",
                                                "schema": {
                                                  "nullable": true,
                                                  "additionalProperties": {
                                                    "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyResult"
                                                  },
                                                  "type": "object"
                                                }
                                              },
                                              "summary": ""
                                            },
                                            "offsetDateTime": {
                                              "description": "",
                                              "errors": [],
                                              "name": "offsetDateTime",
                                              "paramStructure": "either",
                                              "params": [
                                                {
                                                  "name": "date",
                                                  "schema": {
                                                    "nullable": true,
                                                    "format": "date-time",
                                                    "type": "string"
                                                  }
                                                }
                                              ],
                                              "result": {
                                                "name": "result",
                                                "schema": {
                                                  "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyResult"
                                                }
                                              },
                                              "summary": ""
                                            },
                                            "paramTypes": {
                                              "description": "",
                                              "errors": [],
                                              "name": "paramTypes",
                                              "paramStructure": "either",
                                              "params": [
                                                {
                                                  "name": "object",
                                                  "schema": {
                                                    "nullable": true,
                                                    "additionalProperties": true,
                                                    "type": "object"
                                                  }
                                                },
                                                {
                                                  "name": "bool",
                                                  "schema": {
                                                    "nullable": false,
                                                    "type": "boolean"
                                                  }
                                                },
                                                {
                                                  "name": "boolWrapper",
                                                  "schema": {
                                                    "nullable": true,
                                                    "type": "boolean"
                                                  }
                                                },
                                                {
                                                  "name": "integer",
                                                  "schema": {
                                                    "nullable": false,
                                                    "format": "int32",
                                                    "type": "integer"
                                                  }
                                                },
                                                {
                                                  "name": "intWrapper",
                                                  "schema": {
                                                    "nullable": true,
                                                    "format": "int32",
                                                    "type": "integer"
                                                  }
                                                },
                                                {
                                                  "name": "longNumber",
                                                  "schema": {
                                                    "nullable": false,
                                                    "format": "int64",
                                                    "type": "integer"
                                                  }
                                                },
                                                {
                                                  "name": "longWrapper",
                                                  "schema": {
                                                    "nullable": true,
                                                    "format": "int64",
                                                    "type": "integer"
                                                  }
                                                },
                                                {
                                                  "name": "string",
                                                  "schema": {
                                                    "nullable": true,
                                                    "type": "string"
                                                  }
                                                },
                                                {
                                                  "name": "model",
                                                  "schema": {
                                                    "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyInput"
                                                  }
                                                },
                                                {
                                                  "name": "objectList",
                                                  "schema": {
                                                    "nullable": true,
                                                    "type": "array",
                                                    "items": {
                                                      "nullable": true,
                                                      "additionalProperties": true,
                                                      "type": "object"
                                                    }
                                                  }
                                                },
                                                {
                                                  "name": "boolWrapperList",
                                                  "schema": {
                                                    "nullable": true,
                                                    "type": "array",
                                                    "items": {
                                                      "nullable": true,
                                                      "type": "boolean"
                                                    }
                                                  }
                                                },
                                                {
                                                  "name": "intWrapperList",
                                                  "schema": {
                                                    "nullable": true,
                                                    "type": "array",
                                                    "items": {
                                                      "nullable": true,
                                                      "format": "int32",
                                                      "type": "integer"
                                                    }
                                                  }
                                                },
                                                {
                                                  "name": "longWrapperList",
                                                  "schema": {
                                                    "nullable": true,
                                                    "type": "array",
                                                    "items": {
                                                      "nullable": true,
                                                      "format": "int64",
                                                      "type": "integer"
                                                    }
                                                  }
                                                },
                                                {
                                                  "name": "stringList",
                                                  "schema": {
                                                    "nullable": true,
                                                    "type": "array",
                                                    "items": {
                                                      "nullable": true,
                                                      "type": "string"
                                                    }
                                                  }
                                                },
                                                {
                                                  "name": "modelList",
                                                  "schema": {
                                                    "nullable": true,
                                                    "type": "array",
                                                    "items": {
                                                      "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyInput"
                                                    }
                                                  }
                                                },
                                                {
                                                  "name": "objectMap",
                                                  "schema": {
                                                    "nullable": true,
                                                    "additionalProperties": {
                                                      "nullable": true,
                                                      "additionalProperties": true,
                                                      "type": "object"
                                                    },
                                                    "type": "object"
                                                  }
                                                },
                                                {
                                                  "name": "boolWrapperMap",
                                                  "schema": {
                                                    "nullable": true,
                                                    "additionalProperties": {
                                                      "nullable": true,
                                                      "type": "boolean"
                                                    },
                                                    "type": "object"
                                                  }
                                                },
                                                {
                                                  "name": "intWrapperMap",
                                                  "schema": {
                                                    "nullable": true,
                                                    "additionalProperties": {
                                                      "nullable": true,
                                                      "format": "int32",
                                                      "type": "integer"
                                                    },
                                                    "type": "object"
                                                  }
                                                },
                                                {
                                                  "name": "longWrapperMap",
                                                  "schema": {
                                                    "nullable": true,
                                                    "additionalProperties": {
                                                      "nullable": true,
                                                      "format": "int64",
                                                      "type": "integer"
                                                    },
                                                    "type": "object"
                                                  }
                                                },
                                                {
                                                  "name": "stringMap",
                                                  "schema": {
                                                    "nullable": true,
                                                    "additionalProperties": {
                                                      "nullable": true,
                                                      "type": "string"
                                                    },
                                                    "type": "object"
                                                  }
                                                },
                                                {
                                                  "name": "modelMap",
                                                  "schema": {
                                                    "nullable": true,
                                                    "additionalProperties": {
                                                      "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyInput"
                                                    },
                                                    "type": "object"
                                                  }
                                                }
                                              ],
                                              "result": {
                                                "name": "result",
                                                "schema": {
                                                  "nullable": true,
                                                  "type": "string"
                                                }
                                              },
                                              "summary": ""
                                            },
                                            "req": {
                                              "description": "",
                                              "errors": [],
                                              "name": "req",
                                              "paramStructure": "either",
                                              "params": [
                                                {
                                                  "name": "input",
                                                  "schema": {
                                                    "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyInput"
                                                  }
                                                }
                                              ],
                                              "result": {
                                                "name": "result",
                                                "schema": {
                                                  "nullable": true,
                                                  "additionalProperties": {
                                                    "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyResult"
                                                  },
                                                  "type": "object"
                                                }
                                              },
                                              "summary": ""
                                            },
                                            "test1": {
                                              "description": "",
                                              "errors": [],
                                              "name": "test1",
                                              "paramStructure": "either",
                                              "params": [],
                                              "result": {
                                                "name": "result",
                                                "schema": {
                                                  "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyResult"
                                                }
                                              },
                                              "summary": ""
                                            },
                                            "test2": {
                                              "description": "",
                                              "errors": [],
                                              "name": "test2",
                                              "paramStructure": "either",
                                              "params": [],
                                              "result": {
                                                "name": "result",
                                                "schema": {
                                                  "nullable": true,
                                                  "additionalProperties": {
                                                    "$ref": "#/schemas/test.p.JsonRpcEndpoints.MyResult"
                                                  },
                                                  "type": "object"
                                                }
                                              },
                                              "summary": ""
                                            }
                                          }
                                        }""",
                                new SimplePrettyFormatter(new JsonMapperImpl(List.of(new ObjectJsonCodec()), c -> empty()))
                                        .apply(new String(in.readAllBytes(), UTF_8)));
                    } catch (final IOException e) {
                        fail(e);
                    }
                }, release));
    }

    @Test
    void httpEndpoint(@TempDir final Path work) throws IOException {
        new Compiler(work, "HttpEndpoints").compileAndAsserts(this::assertHttpEndpoints);
    }

    @Test
    void httpEndpointNoJsonRpc(@TempDir final Path work) throws IOException {
        new Compiler(work, "HttpEndpoints")
                .classpathFilter(c -> !c.getName().contains("jsonrpc"))
                .compileAndAsserts(this::assertHttpEndpoints);
    }

    @Test
    void httpEndpointNoJson(@TempDir final Path work) throws IOException {
        new Compiler(work, "HttpNoJson")
                .classpathFilter(c -> !c.getName().contains("json") &&
                        !c.getName().contains("cli") &&
                        !c.getName().contains("persistence"))
                .compileAndAsserts((loader, container) -> assertEquals(
                        List.of(
                                "test.p.HttpNoJson$all$FusionHttpEndpoint",
                                "test.p.HttpNoJson$get$FusionHttpEndpoint",
                                "test.p.HttpNoJson$getAndEndsWithFooPath$FusionHttpEndpoint",
                                "test.p.HttpNoJson$getAndFooPath$FusionHttpEndpoint",
                                "test.p.HttpNoJson$getAndRegexFooPath$FusionHttpEndpoint",
                                "test.p.HttpNoJson$getAndStartsWithFooPath$FusionHttpEndpoint"),
                        container.getBeans().getBeans().keySet().stream()
                                .filter(Class.class::isInstance)
                                .map(Class.class::cast)
                                .map(Class::getName)
                                .filter(it -> it.endsWith("$FusionHttpEndpoint"))
                                .sorted()
                                .toList()));
    }

    @Test
    void command(@TempDir final Path work) throws IOException {
        final var compiler = new Compiler(work, "Commands");
        compiler.compileAndAsserts((loader, container) -> {
            assertEquals(
                    List.of("test.p.Commands$C1$FusionCliCommand"),
                    container.getBeans().getBeans().keySet().stream()
                            .filter(Class.class::isInstance)
                            .map(Class.class::cast)
                            .map(Class::getName)
                            .filter(it -> it.endsWith("$FusionCliCommand"))
                            .sorted()
                            .toList());

            System.clearProperty("test.p.Commands$C1");
            withInstance(container, loader, "io.yupiik.fusion.cli.CliAwaiter", CliAwaiter.class, CliAwaiter::await);
            assertEquals("conf=Conf[name=set from test, nested=Nested[lower=45], nesteds=[Nested[lower=123]], list=[first, second]], bean = true", System.clearProperty("test.p.Commands$C1"));
        }, new BaseBean<Args>(Args.class, DefaultScoped.class, 1000, Map.of()) {
            @Override
            public Args create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                return new Args(List.of(
                        "c1",
                        "--c1-name", "set from test",
                        "--c1-nested-lower", "45",
                        "--c1-nesteds-length", "1",
                        "--c1-nesteds-0-lower", "123",
                        "--c1-list", "first,second"));
            }
        });
    }

    @Test
    void commandNoArg(@TempDir final Path work) throws IOException {
        final var compiler = new Compiler(work, "NoArgCommand");
        compiler.compileAndAsserts((loader, container) -> {
            assertEquals(
                    List.of("test.p.NoArgCommand$FusionCliCommand"),
                    container.getBeans().getBeans().keySet().stream()
                            .filter(Class.class::isInstance)
                            .map(Class.class::cast)
                            .map(Class::getName)
                            .filter(it -> it.endsWith("$FusionCliCommand"))
                            .sorted()
                            .toList());

            System.clearProperty("test.p.NoArgCommand");
            withInstance(container, loader, "io.yupiik.fusion.cli.CliAwaiter", CliAwaiter.class, CliAwaiter::await);
            assertEquals("true", System.clearProperty("test.p.NoArgCommand"));
        }, new ProvidedInstanceBean<>(DefaultScoped.class, Args.class, () -> new Args(List.of("c"))));
    }

    @Test
    void commandUsage(@TempDir final Path work) throws IOException {
        new Compiler(work, "Commands").compileAndAsserts((loader, container) -> assertEquals(
                        """
                                Missing command 'unknown':
                                * c1:
                                  A super command.
                                  Parameters:
                                    --c1-list: -
                                    --c1-name: The main name.
                                    --c1-nested-lower: -
                                    --c1-nesteds-$index-lower: -
                                """,
                        assertThrows(IllegalArgumentException.class, () ->
                                withInstance(container, loader, "io.yupiik.fusion.cli.CliAwaiter", CliAwaiter.class, CliAwaiter::await))
                                .getMessage()),
                new BaseBean<Args>(Args.class, DefaultScoped.class, 1000, Map.of()) {
                    @Override
                    public Args create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return new Args(List.of("unknown"));
                    }
                });
    }

    @Test
    void simplePersistence(@TempDir final Path work) throws IOException {
        final var entity = "persistence.SimpleFlatEntity";
        final var compiler = new Compiler(work, entity);
        compiler.compileAndAsserts((loader, container) -> {
            assertEquals(
                    """
                            package test.p.persistence;
                                                    
                                                    
                            @io.yupiik.fusion.framework.api.container.Generation(version = 1)
                            public class SimpleFlatEntity$FusionPersistenceEntity extends io.yupiik.fusion.persistence.impl.BaseEntity<SimpleFlatEntity, java.lang.String> {
                                public SimpleFlatEntity$FusionPersistenceEntity(final io.yupiik.fusion.persistence.impl.DatabaseConfiguration configuration) {
                                    super(
                                      configuration,
                                      SimpleFlatEntity.class,
                                      "SIMPLE_FLAT_ENTITY",
                                      java.util.List.of(
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("id", java.lang.String.class, "id", 0, false),
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("name", java.lang.String.class, "name"),
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("arr", byte[].class, "arr"),
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("age", int.class, "SIMPLE_AGE"),
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("kind", test.p.persistence.SimpleFlatEntity.Kind.class, "kind")
                                      ),
                                      false,
                                      (entity, statement) -> {
                                        final var instance = entity.onInsert();
                                        if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                        if (instance.name() == null) { statement.setNull(2, java.sql.Types.VARCHAR); } else { statement.setString(2, instance.name()); }
                                        if (instance.arr() == null) { statement.setNull(3, java.sql.Types.VARBINARY); } else { statement.setBytes(3, instance.arr()); }
                                        statement.setInt(4, instance.age());
                                        if (instance.kind() == null) { statement.setNull(5, java.sql.Types.VARCHAR); } else { statement.setString(5, instance.kind().name()); }
                                        return instance;
                                      },
                                      (instance, statement) -> {
                                        if (instance.name() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.name()); }
                                        if (instance.arr() == null) { statement.setNull(2, java.sql.Types.VARBINARY); } else { statement.setBytes(2, instance.arr()); }
                                        statement.setInt(3, instance.age());
                                        if (instance.kind() == null) { statement.setNull(4, java.sql.Types.VARCHAR); } else { statement.setString(4, instance.kind().name()); }
                                        if (instance.id() == null) { statement.setNull(5, java.sql.Types.VARCHAR); } else { statement.setString(5, instance.id()); }
                                        return instance;
                                      },
                                      (instance, statement) -> {
                                        if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                      },
                                      (id, statement) -> {
                                        if (id == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, id); }
                                      },
                                      (entity, statement) -> entity,
                                      columns -> {
                                        final var id = stringOf(columns.indexOf("id"));
                                        final var name = stringOf(columns.indexOf("name"));
                                        final var arr = bytesOf(columns.indexOf("arr"));
                                        final var age = intOf(columns.indexOf("simple_age"), true);
                                        final var kind = enumOf(columns.indexOf("kind"), test.p.persistence.SimpleFlatEntity.Kind.class);
                                        return rset -> {
                                          try {
                                            final var entity = new test.p.persistence.SimpleFlatEntity(id.apply(rset), name.apply(rset), arr.apply(rset), age.apply(rset), kind.apply(rset));
                                            entity.onLoad();
                                            return entity;
                                          } catch (final java.sql.SQLException e) {
                                            throw new io.yupiik.fusion.persistence.api.PersistenceException(e);
                                          }
                                        };
                                      });
                                }
                            }
                                                    
                            """,
                    compiler.readGeneratedSource(entity + "$FusionPersistenceEntity"));

            final var databaseConfiguration = container.lookup(DatabaseConfiguration.class);
            assertNotNull(databaseConfiguration.instance());

            // no datasource in this test
            assertNull(databaseConfiguration.instance().getDataSource());

            // force a translation to avoid to guess it since we don't have any datasource
            databaseConfiguration.instance().setTranslation(new DefaultTranslation());

            final var database = container.lookup(Database.class);
            assertNotNull(database.instance());

            // init with database init so tested after (until user sets it explicitly but not our case)
            final var model = (Entity<?, ?>) databaseConfiguration.instance().getInstanceLookup().apply(loader.apply("test.p." + entity));
            assertNotNull(model);
            assertEquals("SIMPLE_FLAT_ENTITY", model.getTable());
        });
    }

    @Test
    void persistenceOnDelete(@TempDir final Path work) throws IOException {
        final var entity = "persistence.OnDeleteEntity";
        final var compiler = new Compiler(work, entity);
        compiler.compileAndAsserts((loader, container) -> assertEquals(
                """
                        package test.p.persistence;
                                                
                                                
                        @io.yupiik.fusion.framework.api.container.Generation(version = 1)
                        public class OnDeleteEntity$FusionPersistenceEntity extends io.yupiik.fusion.persistence.impl.BaseEntity<OnDeleteEntity, java.lang.String> {
                            public OnDeleteEntity$FusionPersistenceEntity(final io.yupiik.fusion.persistence.impl.DatabaseConfiguration configuration) {
                                super(
                                  configuration,
                                  OnDeleteEntity.class,
                                  "ON_DELETE",
                                  java.util.List.of(
                                    new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("id", java.lang.String.class, "id", 0, false)
                                  ),
                                  false,
                                  (instance, statement) -> {
                                    if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                    return instance;
                                  },
                                  (instance, statement) -> {
                                                
                                    if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                    return instance;
                                  },
                                  (instance, statement) -> {
                                    instance.deleted();
                                    if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                  },
                                  (id, statement) -> {
                                    if (id == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, id); }
                                  },
                                  (entity, statement) -> entity,
                                  columns -> {
                                    final var id = stringOf(columns.indexOf("id"));
                                    return rset -> {
                                      try {
                                        return new test.p.persistence.OnDeleteEntity(id.apply(rset));
                                      } catch (final java.sql.SQLException e) {
                                        throw new io.yupiik.fusion.persistence.api.PersistenceException(e);
                                      }
                                    };
                                  });
                            }
                        }
                                                
                        """,
                compiler.readGeneratedSource(entity + "$FusionPersistenceEntity")));
    }

    @Test
    void persistenceInjectionOnCallbacks(@TempDir final Path work) throws IOException {
        final var entity = "persistence.CallbackWithInjections";
        final var compiler = new Compiler(work, entity, "Bean2");
        compiler.compileAndAsserts((loader, container) -> assertEquals(
                """
                        package test.p.persistence;
                                                
                                                
                        @io.yupiik.fusion.framework.api.container.Generation(version = 1)
                        public class CallbackWithInjections$FusionPersistenceEntity extends io.yupiik.fusion.persistence.impl.BaseEntity<CallbackWithInjections, java.lang.String> {
                            public CallbackWithInjections$FusionPersistenceEntity(final io.yupiik.fusion.persistence.impl.DatabaseConfiguration configuration, final io.yupiik.fusion.framework.api.RuntimeContainer main__container) {
                                super(
                                  configuration,
                                  CallbackWithInjections.class,
                                  "CB_INJECTION",
                                  java.util.List.of(
                                    new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("id", java.lang.String.class, "id", 0, false)
                                  ),
                                  false,
                                  (entity, statement) -> {
                                    var instance = entity;
                                    try (final var bean = main__container.lookup(test.p.Bean2.class)) {
                                      instance = entity.onInsert(bean.instance());
                                    }
                                    if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                    return instance;
                                  },
                                  (entity, statement) -> {
                                    var instance = entity;
                                    try (final var bean = main__container.lookup(test.p.Bean2.class)) {
                                      instance = entity.onUpdate(bean.instance());
                                    }
                                                
                                    if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                    return instance;
                                  },
                                  (instance, statement) -> {
                                    try (final var bean = main__container.lookup(test.p.Bean2.class)) {
                                      instance.onDelete(bean.instance());
                                    }
                                    if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                  },
                                  (id, statement) -> {
                                    if (id == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, id); }
                                  },
                                  (entity, statement) -> entity,
                                  columns -> {
                                    final var id = stringOf(columns.indexOf("id"));
                                    return rset -> {
                                      try {
                                        final var entity = new test.p.persistence.CallbackWithInjections(id.apply(rset));
                                        try (final var bean = main__container.lookup(test.p.Bean2.class)) {
                                      entity.onLoad(bean.instance());
                                    }
                                        return entity;
                                      } catch (final java.sql.SQLException e) {
                                        throw new io.yupiik.fusion.persistence.api.PersistenceException(e);
                                      }
                                    };
                                  });
                            }
                        }
                                                
                        """,
                compiler.readGeneratedSource(entity + "$FusionPersistenceEntity")));
    }

    @Test
    void nestedRecordPersistence(@TempDir final Path work) throws IOException {
        final var entity = "persistence.NestedEntity";
        final var compiler = new Compiler(work, entity);
        compiler.compileAndAsserts((loader, container) -> {
            assertEquals(
                    """
                            package test.p.persistence;
                                                        
                                                        
                            @io.yupiik.fusion.framework.api.container.Generation(version = 1)
                            public class NestedEntity$FusionPersistenceEntity extends io.yupiik.fusion.persistence.impl.BaseEntity<NestedEntity, java.lang.String> {
                                public NestedEntity$FusionPersistenceEntity(final io.yupiik.fusion.persistence.impl.DatabaseConfiguration configuration) {
                                    super(
                                      configuration,
                                      NestedEntity.class,
                                      "NESTED_ENTITY",
                                      java.util.List.of(
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("id", java.lang.String.class, "id", 0, false),
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("name", java.lang.String.class, "name"),
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("arr", byte[].class, "arr"),
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("age", int.class, "SIMPLE_AGE"),
                                        new io.yupiik.fusion.persistence.impl.ColumnMetadataImpl("kind", test.p.persistence.NestedEntity.Kind.class, "kind")
                                      ),
                                      false,
                                      (instance, statement) -> {
                                        if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                        if (instance.name() == null) { statement.setNull(2, java.sql.Types.VARCHAR); } else { statement.setString(2, instance.name()); }
                                        if (instance.arr() == null) { statement.setNull(3, java.sql.Types.VARBINARY); } else { statement.setBytes(3, instance.arr()); }
                                        statement.setInt(4, instance.nested().age());
                                        if (instance.nested() == null || instance.nested().kind() == null) { statement.setNull(5, java.sql.Types.VARCHAR); } else { statement.setString(5, instance.nested().kind().name()); }
                                        return instance;
                                      },
                                      (instance, statement) -> {
                                        if (instance.name() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.name()); }
                                        if (instance.arr() == null) { statement.setNull(2, java.sql.Types.VARBINARY); } else { statement.setBytes(2, instance.arr()); }
                                        statement.setInt(3, instance.nested().age());
                                        if (instance.nested() == null || instance.nested().kind() == null) { statement.setNull(4, java.sql.Types.VARCHAR); } else { statement.setString(4, instance.nested().kind().name()); }
                                        if (instance.id() == null) { statement.setNull(5, java.sql.Types.VARCHAR); } else { statement.setString(5, instance.id()); }
                                        return instance;
                                      },
                                      (instance, statement) -> {
                                        if (instance.id() == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, instance.id()); }
                                      },
                                      (id, statement) -> {
                                        if (id == null) { statement.setNull(1, java.sql.Types.VARCHAR); } else { statement.setString(1, id); }
                                      },
                                      (entity, statement) -> entity,
                                      columns -> {
                                        final var id = stringOf(columns.indexOf("id"));
                                        final var name = stringOf(columns.indexOf("name"));
                                        final var arr = bytesOf(columns.indexOf("arr"));
                                        final var age = intOf(columns.indexOf("simple_age"), true);
                                        final var kind = enumOf(columns.indexOf("kind"), test.p.persistence.NestedEntity.Kind.class);
                                        final var nested = (java.util.function.Function<java.sql.ResultSet, test.p.persistence.NestedEntity.Nested>)  rset -> {
                                          try {
                                            return new test.p.persistence.NestedEntity.Nested(age.apply(rset), kind.apply(rset));
                                          } catch (final java.sql.SQLException e) {
                                            throw new io.yupiik.fusion.persistence.api.PersistenceException(e);
                                          }
                                        };
                                        return rset -> {
                                          try {
                                            return new test.p.persistence.NestedEntity(id.apply(rset), name.apply(rset), arr.apply(rset), nested.apply(rset));
                                          } catch (final java.sql.SQLException e) {
                                            throw new io.yupiik.fusion.persistence.api.PersistenceException(e);
                                          }
                                        };
                                      });
                                }
                            }
                                                        
                            """,
                    compiler.readGeneratedSource(entity + "$FusionPersistenceEntity"));

            final var databaseConfiguration = container.lookup(DatabaseConfiguration.class);
            assertNotNull(databaseConfiguration.instance());

            // no datasource in this test
            assertNull(databaseConfiguration.instance().getDataSource());

            // force a translation to avoid to guess it since we don't have any datasource
            databaseConfiguration.instance().setTranslation(new DefaultTranslation());

            final var database = container.lookup(Database.class);
            assertNotNull(database.instance());

            // init with database init so tested after (until user sets it explicitly but not our case)
            final var model = (Entity<?, ?>) databaseConfiguration.instance().getInstanceLookup().apply(loader.apply("test.p." + entity));
            assertNotNull(model);
            assertEquals("NESTED_ENTITY", model.getTable());
        });
    }

    private <A> void withInstance(final RuntimeContainer container, final Function<String, Class<?>> loader,
                                  final String name,
                                  final Class<A> type, final Consumer<A> consumer) {
        try (final var instance = container.lookup(loader.apply(name))) {
            consumer.accept(type.cast(instance.instance()));
        }
    }

    private void assertJsonRpc(final JsonRpcEndpoint instance, final String request, final String response) {
        final var handled = assertDoesNotThrow(() -> instance.handle(jsonRpc(request))
                .toCompletableFuture().get());
        assertEquals(200, handled.status());
        assertEquals(Map.of("content-type", List.of("application/json;charset=utf-8")), handled.headers());
        assertEquals(response, assertDoesNotThrow(() -> new String(new RequestBodyAggregator(handled.body(), UTF_8).promise().toCompletableFuture().get())));
    }

    private Request jsonRpc(final String payload) {
        return new SimpleRequest("POST", "/jsonrpc", new HashMap<>(), new BytesPublisher(payload));
    }

    private void assertHttpEndpoints(final Function<String, Class<?>> loader, final RuntimeContainer container) {
        assertEquals(
                List.of(
                        "test.p.HttpEndpoints$all$FusionHttpEndpoint",
                        "test.p.HttpEndpoints$get$FusionHttpEndpoint",
                        "test.p.HttpEndpoints$getAndEndsWithFooPath$FusionHttpEndpoint",
                        "test.p.HttpEndpoints$getAndFooPath$FusionHttpEndpoint",
                        "test.p.HttpEndpoints$getAndRegexFooPath$FusionHttpEndpoint",
                        "test.p.HttpEndpoints$getAndStartsWithFooPath$FusionHttpEndpoint",
                        "test.p.HttpEndpoints$greet$FusionHttpEndpoint",
                        "test.p.HttpEndpoints$greetStage$FusionHttpEndpoint"),
                container.getBeans().getBeans().keySet().stream()
                        .filter(Class.class::isInstance)
                        .map(Class.class::cast)
                        .map(Class::getName)
                        .filter(it -> it.endsWith("$FusionHttpEndpoint"))
                        .sorted()
                        .toList());

        withInstance(container, loader, "test.p.HttpEndpoints$all$FusionHttpEndpoint", Endpoint.class, instance -> {
            assertTrue(instance.matches(new SimpleRequest()));
            assertTrue(instance.matches(new SimpleRequest("POST", "/foo", Map.of())));
            assertTrue(instance.matches(new SimpleRequest("POST", "/whatever", Map.of())));
        });
        withInstance(container, loader, "test.p.HttpEndpoints$get$FusionHttpEndpoint", Endpoint.class, instance -> {
            assertTrue(instance.matches(new SimpleRequest()));
            assertFalse(instance.matches(new SimpleRequest("POST", "/foo", Map.of())));
            assertTrue(instance.matches(new SimpleRequest("GET", "/whatever", Map.of())));
        });
        withInstance(container, loader, "test.p.HttpEndpoints$getAndFooPath$FusionHttpEndpoint", Endpoint.class, instance -> {
            assertTrue(instance.matches(new SimpleRequest()));
            assertFalse(instance.matches(new SimpleRequest("POST", "/foo", Map.of())));
            assertFalse(instance.matches(new SimpleRequest("GET", "/whatever", Map.of())));
        });
        withInstance(container, loader, "test.p.HttpEndpoints$getAndEndsWithFooPath$FusionHttpEndpoint", Endpoint.class, instance -> {
            assertTrue(instance.matches(new SimpleRequest()));
            assertFalse(instance.matches(new SimpleRequest("GET", "/foo/bar", Map.of())));
            assertTrue(instance.matches(new SimpleRequest("GET", "/whatever/foo", Map.of())));
        });
        withInstance(container, loader, "test.p.HttpEndpoints$getAndStartsWithFooPath$FusionHttpEndpoint", Endpoint.class, instance -> {
            assertTrue(instance.matches(new SimpleRequest()));
            assertTrue(instance.matches(new SimpleRequest("GET", "/foo/bar", Map.of())));
            assertFalse(instance.matches(new SimpleRequest("GET", "/whatever/foo", Map.of())));
        });
        withInstance(container, loader, "test.p.HttpEndpoints$getAndRegexFooPath$FusionHttpEndpoint", Endpoint.class, instance -> {
            assertFalse(instance.matches(new SimpleRequest()));

            final var attributes = new HashMap<String, Object>();
            assertTrue(instance.matches(new SimpleRequest("GET", "/foo/bar/foo", attributes)));

            final var matcher = attributes.get("fusion.http.matcher");
            assertInstanceOf(Matcher.class, matcher);
        });
        withInstance(container, loader, "test.p.HttpEndpoints$greet$FusionHttpEndpoint", Endpoint.class, instance -> {
            assertFalse(instance.matches(new SimpleRequest()));
            assertTrue(instance.matches(new SimpleRequest("POST", "/greet", Map.of())));

            final var response = assertDoesNotThrow(() -> instance.handle(
                            new SimpleRequest("POST", "/greet", Map.of(), new BytesPublisher("{\"name\":\"fusion\"}")))
                    .toCompletableFuture().get());
            assertEquals(200, response.status());
            assertEquals(Map.of("content-type", List.of("application/json;charset=utf-8")), response.headers());
            assertEquals("{\"message\":\"Hello fusion!\"}", assertDoesNotThrow(() -> new String(new RequestBodyAggregator(response.body(), UTF_8).promise().toCompletableFuture().get())));
        });
        withInstance(container, loader, "test.p.HttpEndpoints$greetStage$FusionHttpEndpoint", Endpoint.class, instance -> {
            assertFalse(instance.matches(new SimpleRequest()));
            assertTrue(instance.matches(new SimpleRequest("POST", "/greetstage", Map.of())));

            final var response = assertDoesNotThrow(() -> instance.handle(
                            new SimpleRequest("POST", "/greetstage", Map.of(), new BytesPublisher("{\"name\":\"fusion\"}")))
                    .toCompletableFuture().get());
            assertEquals(200, response.status());
            assertEquals(Map.of("content-type", List.of("application/json;charset=utf-8")), response.headers());
            assertEquals("{\"message\":\"Hello fusion!\"}", assertDoesNotThrow(() -> new String(new RequestBodyAggregator(response.body(), UTF_8).promise().toCompletableFuture().get())));
        });
    }

    private DynamicTest lockedDynamicTest(final Semaphore semaphore, final AtomicInteger counter,
                                          final String name, final Executable test, final Runnable release) {
        return dynamicTest(name, () -> {
            semaphore.acquire();
            try {
                test.execute();
            } finally {
                semaphore.release();
                if (counter.decrementAndGet() == 0) {
                    release.run();
                }
            }
        });
    }

    private String simpleFormat(InputStream in) throws IOException {
        return new SimplePrettyFormatter(new JsonMapperImpl(List.of(), key -> Optional.empty())).apply(new String(in.readAllBytes(), UTF_8));
    }

    private record SimpleRequest(String method, String path, Map<String, Object> attributes,
                                 Flow.Publisher<ByteBuffer> body) implements Request {
        private SimpleRequest() {
            this("GET", "/foo", new HashMap<>(), null);
        }

        private SimpleRequest(final String method, final String path, final Map<String, Object> attributes) {
            this(method, path, attributes, null);
        }

        @Override
        public String scheme() {
            return "http";
        }

        @Override
        public String query() {
            return null;
        }

        @Override
        public Body fullBody() {
            return new Body() {
                @Override
                public Body cached() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CompletionStage<String> string() {
                    throw new UnsupportedOperationException("test");
                }

                @Override
                public CompletionStage<byte[]> bytes() {
                    throw new UnsupportedOperationException("test");
                }

                @Override
                public String parameter(final String name) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
                    body.subscribe(subscriber);
                }
            };
        }

        @Override
        public Stream<Cookie> cookies() {
            return Stream.empty();
        }

        @Override
        public String parameter(final String name) {
            return null;
        }

        @Override
        public Map<String, String[]> parameters() {
            return Map.of();
        }

        @Override
        public String header(final String name) {
            return null;
        }

        @Override
        public Map<String, List<String>> headers() {
            return Map.of();
        }

        @Override
        public <T> T attribute(final String key, final Class<T> type) {
            return type.cast(attributes.get(key));
        }

        @Override
        public <T> void setAttribute(final String key, final T value) {
            attributes.put(key, value);
        }
    }
}
