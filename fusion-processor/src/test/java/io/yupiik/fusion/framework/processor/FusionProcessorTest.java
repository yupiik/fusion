package io.yupiik.fusion.framework.processor;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext;
import io.yupiik.fusion.framework.api.container.context.subclass.SupplierDelegatingContext;
import io.yupiik.fusion.framework.processor.test.Compiler;
import io.yupiik.fusion.http.server.api.Cookie;
import io.yupiik.fusion.http.server.api.Request;
import io.yupiik.fusion.http.server.spi.Endpoint;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FusionProcessorTest {
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
                                        
                        import test.p.Bean1;
                                        
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
                        import io.yupiik.fusion.framework.api.container.FusionListener;
                        import io.yupiik.fusion.framework.api.container.FusionModule;
                                        
                        public class FusionGeneratedModule implements FusionModule {
                            @Override
                            public Stream<FusionBean<?>> beans() {
                                return Stream.of(
                                    new test.p.Bean1$FusionBean(),
                                    new test.p.Bean2$FusionBean()
                                );
                            }
                                        
                            @Override
                            public Stream<FusionListener<?>> listeners() {
                                return Stream.of(
                                        
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
        new Compiler(work, "RecordConfiguration", "NestedConf", "TestConf")
                .compileAndAsserts(
                        instance -> {
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
                                            "list=[ab,cde,fgh]" +
                                            "]",
                                    instance.instance().toString());

                            // doc
                            try (final var in = requireNonNull(instance.instance().getClass().getClassLoader()
                                    .getResourceAsStream("META-INF/fusion/configuration/documentation.json"))) {
                                assertEquals("{" +
                                        "\"version\":1," +
                                        "\"classes\":{" +
                                        "\"test.p.NestedConf\":[" +
                                        "{\"name\":\"nestedValue\",\"documentation\":\"The nested main value.\",\"required\":false}," +
                                        "{\"ref\":\"test.p.NestedConf.Nest2\",\"name\":\"second\",\"documentation\":\"\",\"required\":false}" +
                                        "]," +
                                        "\"test.p.NestedConf.Nest2\":[" +
                                        "{\"name\":\"value\",\"documentation\":\"Some int.\",\"required\":false}]," +
                                        "\"test.p.RecordConfiguration\":[{\"name\":\"app.age\",\"documentation\":\"\",\"required\":false}," +
                                        "{\"name\":\"app.bigInt\",\"documentation\":\"\",\"required\":false},{\"name\":\"app.bigNumber\",\"documentation\":\"\",\"required\":false}," +
                                        "{\"name\":\"app.list\",\"documentation\":\"\",\"required\":false}," +
                                        "{\"name\":\"app.name\",\"documentation\":\"The app name\",\"required\":false}," +
                                        "{\"ref\":\"test.p.NestedConf\",\"name\":\"app.nested\",\"documentation\":\"\",\"required\":false}," +
                                        "{\"ref\":\"test.p.NestedConf\",\"name\":\"app.nesteds.$index\",\"documentation\":\"\",\"required\":false}," +
                                        "{\"name\":\"app.number\",\"documentation\":\"\",\"required\":false}," +
                                        "{\"name\":\"app.toggle\",\"documentation\":\"\",\"required\":false}" +
                                        "]" +
                                        "}" +
                                        "}", new String(in.readAllBytes(), UTF_8));
                            } catch (final IOException e) {
                                fail(e);
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
                                "\"zonedList\":[\"2022-12-06T15:19Z\",\"2022-12-06T15:19:49Z\"]," +
                                // @JsonOthers - pushed at the end by default sorting (serialization)
                                "\"fall\":\"back\",\"fall-obj\":{\"down\":1},\"fall-list\":[5],\"unmapped\":true}",
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
                                assertEquals("" +
                                                "{\"schemas\":{" +
                                                "\"test.p.JsonRecords.AllInOne\":{" +
                                                "\"$id\":\"test.p.JsonRecords.AllInOne\"," +
                                                "\"type\":\"object\",\"properties\":{" +
                                                "\"aBool\":{\"type\":\"boolean\"}," +
                                                "\"bigNumber\":{\"type\":\"string\",\"nullable\":true}," +
                                                "\"integer\":{\"type\":\"integer\",\"format\":\"int32\"}," +
                                                "\"nullableInt\":{\"type\":\"integer\",\"format\":\"int32\",\"nullable\":true}," +
                                                "\"lg\":{\"type\":\"number\",\"format\":\"int64\"}," +
                                                "\"more\":{\"type\":\"number\"}," +
                                                "\"simplest\":{\"type\":\"string\",\"nullable\":true}," +
                                                "\"date\":{\"type\":\"string\",\"format\":\"date\",\"nullable\":true}," +
                                                "\"dateTime\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?$\",\"nullable\":true}," +
                                                "\"offset\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$\",\"nullable\":true}," +
                                                "\"zoned\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\\\[.*\\\\])?$\",\"nullable\":true}," +
                                                "\"generic\":{\"type\":\"object\",\"additionalProperties\":true,\"nullable\":true}," +
                                                "\"nested\":{\"$ref\":\"#/schemas/test.p.JsonRecords.StringHolder\",\"nullable\":true}," +
                                                "\"booleanList\":{\"type\":\"array\",\"items\":{\"type\":\"boolean\",\"nullable\":true},\"nullable\":true}," +
                                                "\"bigNumbers\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"nullable\":true},\"nullable\":true}," +
                                                "\"intList\":{\"type\":\"array\",\"items\":{\"type\":\"integer\",\"format\":\"int32\",\"nullable\":true},\"nullable\":true}," +
                                                "\"longList\":{\"type\":\"array\",\"items\":{\"type\":\"number\",\"format\":\"int64\",\"nullable\":true},\"nullable\":true}," +
                                                "\"doubleList\":{\"type\":\"array\",\"items\":{\"type\":\"number\",\"nullable\":true},\"nullable\":true}," +
                                                "\"stringList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"nullable\":true},\"nullable\":true}," +
                                                "\"dateList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"format\":\"date\",\"nullable\":true},\"nullable\":true}," +
                                                "\"dateTimeList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?$\",\"nullable\":true}," +
                                                "\"nullable\":true},\"offsetList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$\",\"nullable\":true}," +
                                                "\"nullable\":true},\"zonedList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\\\[.*\\\\])?$\",\"nullable\":true}," +
                                                "\"nullable\":true},\"genericList\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"additionalProperties\":true,\"nullable\":true},\"nullable\":true}," +
                                                "\"nestedList\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/schemas/test.p.JsonRecords.StringHolder\",\"nullable\":true},\"nullable\":true}," +
                                                "\"mapStringString\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\",\"nullable\":true},\"nullable\":true}," +
                                                "\"mapStringInt\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\",\"format\":\"int32\",\"nullable\":true},\"nullable\":true}," +
                                                "\"mapNested\":{\"type\":\"object\",\"additionalProperties\":{\"$ref\":\"#/schemas/test.p.JsonRecords.StringHolder\",\"nullable\":true},\"nullable\":true}," +
                                                "\"others\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"object\",\"additionalProperties\":true,\"nullable\":true},\"nullable\":true}}}," +
                                                "\"test.p.JsonRecords.StringHolder\":{\"$id\":\"test.p.JsonRecords.StringHolder\",\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"nullable\":true}}}," +
                                                "\"test.p.JsonRecords.StrongTyping\":{" +
                                                "\"$id\":\"test.p.JsonRecords.StrongTyping\",\"type\":\"object\",\"properties\":{" +
                                                "\"aBool\":{\"type\":\"boolean\"},\"bigNumber\":{\"type\":\"string\",\"nullable\":true}," +
                                                "\"integer\":{\"type\":\"integer\",\"format\":\"int32\"},\"nullableInt\":{\"type\":\"integer\",\"format\":\"int32\",\"nullable\":true}," +
                                                "\"lg\":{\"type\":\"number\",\"format\":\"int64\"},\"more\":{\"type\":\"number\"}," +
                                                "\"simplest\":{\"type\":\"string\",\"nullable\":true},\"date\":{\"type\":\"string\",\"format\":\"date\",\"nullable\":true}," +
                                                "\"dateTime\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?$\",\"nullable\":true}," +
                                                "\"offset\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$\",\"nullable\":true}," +
                                                "\"zoned\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\\\[.*\\\\])?$\",\"nullable\":true}," +
                                                "\"generic\":{\"type\":\"object\",\"additionalProperties\":true,\"nullable\":true}," +
                                                "\"nested\":{\"$ref\":\"#/schemas/test.p.JsonRecords.StringHolder\",\"nullable\":true}," +
                                                "\"booleanList\":{\"type\":\"array\",\"items\":{\"type\":\"boolean\",\"nullable\":true},\"nullable\":true}," +
                                                "\"bigNumbers\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"nullable\":true},\"nullable\":true}," +
                                                "\"intList\":{\"type\":\"array\",\"items\":{\"type\":\"integer\",\"format\":\"int32\",\"nullable\":true},\"nullable\":true}," +
                                                "\"longList\":{\"type\":\"array\",\"items\":{\"type\":\"number\",\"format\":\"int64\",\"nullable\":true},\"nullable\":true}," +
                                                "\"doubleList\":{\"type\":\"array\",\"items\":{\"type\":\"number\",\"nullable\":true},\"nullable\":true}," +
                                                "\"stringList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"nullable\":true},\"nullable\":true}," +
                                                "\"dateList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"format\":\"date\",\"nullable\":true},\"nullable\":true}," +
                                                "\"dateTimeList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?$\",\"nullable\":true},\"nullable\":true}," +
                                                "\"offsetList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?$\",\"nullable\":true},\"nullable\":true}," +
                                                "\"zonedList\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"pattern\":\"^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?(\\\\.[0-9]*)?([+-]?[0-9]{2}:[0-9]{2})?Z?(\\\\[.*\\\\])?$\",\"nullable\":true},\"nullable\":true}," +
                                                "\"genericList\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"additionalProperties\":true,\"nullable\":true},\"nullable\":true}," +
                                                "\"nestedList\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/schemas/test.p.JsonRecords.StringHolder\",\"nullable\":true},\"nullable\":true}," +
                                                "\"mapStringString\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\",\"nullable\":true},\"nullable\":true}," +
                                                "\"mapStringInt\":{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\",\"format\":\"int32\",\"nullable\":true},\"nullable\":true}," +
                                                "\"mapNested\":{\"type\":\"object\",\"additionalProperties\":{\"$ref\":\"#/schemas/test.p.JsonRecords.StringHolder\",\"nullable\":true},\"nullable\":true}}}}}",
                                        new String(in.readAllBytes(), UTF_8));
                            } catch (final IOException e) {
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
    void jsonModelLoop(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonCycle")
                .jsonRoundTripAsserts("test.p.JsonCycle",
                        "{\"name\":\"child\",\"parent\":{\"name\":\"root\"}}",
                        "JsonCycle[parent=JsonCycle[parent=null, name=root], name=child]",
                        (loader, mapper) -> { // ensure JSON-Schemas are generated even when there is a loop
                            try (final var in = requireNonNull(Thread.currentThread().getContextClassLoader()
                                    .getResourceAsStream("META-INF/fusion/json/schemas.json"))) {
                                assertEquals("{\"schemas\":{" +
                                                "\"test.p.JsonCycle\":{\"$id\":\"test.p.JsonCycle\",\"type\":\"object\",\"properties\":{" +
                                                "\"parent\":{\"$ref\":\"#/schemas/test.p.JsonCycle\",\"nullable\":true},\"name\":{\"type\":\"string\",\"nullable\":true}}}}}",
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

    @Test
    void httpEndpoint(@TempDir final Path work) throws IOException {
        final var compiler = new Compiler(work, "HttpEndpoints");
        compiler.compileAndAsserts((loader, container) -> {
            assertEquals(
                    List.of(
                            "test.p.HttpEndpoints$all$FusionHttpEndpoint",
                            "test.p.HttpEndpoints$get$FusionHttpEndpoint",
                            "test.p.HttpEndpoints$getAndEndsWithFooPath$FusionHttpEndpoint",
                            "test.p.HttpEndpoints$getAndFooPath$FusionHttpEndpoint",
                            "test.p.HttpEndpoints$getAndRegexFooPath$FusionHttpEndpoint",
                            "test.p.HttpEndpoints$getAndStartsWithFooPath$FusionHttpEndpoint"),
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
                assertTrue(instance.matches(new SimpleRequest("GET", "/foo/bar/foo", attributes)));;

                final var matcher = attributes.get("fusion.http.matcher");
                assertInstanceOf(Matcher.class, matcher);
            });
        });
    }

    private <A> void withInstance(final RuntimeContainer container, final Function<String, Class<?>> loader, final String name,
                                  final Class<A> type, final Consumer<A> consumer) {
        try (final var instance = container.lookup(loader.apply(name))) {
            consumer.accept(type.cast(instance.instance()));
        }
    }

    private record SimpleRequest(String method, String path, Map<String, Object> attributes) implements Request {
        private SimpleRequest() {
            this("GET", "/foo", new HashMap<>());
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
        public Flow.Publisher<ByteBuffer> body() {
            return null;
        }

        @Override
        public Stream<Cookie> cookies() {
            return Stream.empty();
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
