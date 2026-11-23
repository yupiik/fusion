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
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.framework.api.container.bean.FunctionalBean;
import io.yupiik.fusion.framework.api.container.bean.ProvidedInstanceBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.processor.test.GeneratedFactories;
import io.yupiik.fusion.persistence.api.Entity;
import io.yupiik.fusion.persistence.api.PersistenceException;
import io.yupiik.fusion.persistence.impl.DatabaseConfiguration;
import io.yupiik.fusion.persistence.impl.FunctionalEntity;
import io.yupiik.fusion.persistence.impl.translation.DefaultTranslation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"unchecked", "rawtypes"})
class CompactPersistenceProcessorTest {
    @TempDir
    Path work;

    @ParameterizedTest
    @ValueSource(ints = {1, 36, 100})
    void realisticEntitiesRetainTypedOperationsAtScale(final int count) throws Exception {
        final var sources = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < count; i++) {
            final var name = "Model" + i;
            sources.put("demo." + name, """
                    package demo;
                    import io.yupiik.fusion.framework.build.api.persistence.*;
                    @Table("orders")
                    public record %s(@Id String tenant, @Id long id, String title, Integer quantity,
                                     Long version, Boolean active, java.math.BigDecimal price,
                                     State state, java.time.LocalDate day, Detail detail) {
                        public enum State { OPEN, CLOSED }
                        @Table(Table.EMBEDDABLE)
                        public record Detail(String street, Integer number, double latitude) {}
                        @OnInsert %s insert() { return this; }
                        @OnLoad void load() {}
                    }
                    """.formatted(name, name));
        }
        compile(sources, "-Afusion.generateModule=false");
        try (final var loader = loader()) {
            for (int i = 0; i < count; i++) {
                final var factory = GeneratedFactories.invoke(loader, "demo", "Model" + i + "$FusionPersistenceEntity", Entity.Factory.class);
                final var entity = factory.create(configuration(), null);
                final var parameters = new java.util.LinkedHashMap<Integer, Object>();
                entity.onFindById(List.of("tenant", 17L), statement(parameters));
                assertEquals(Map.of(1, "tenant", 2, 17L), parameters);
                final var instance = entity.mapper(List.of("tenant", "id", "title", "latitude"))
                        .apply(row("tenant", 17L, "order", 12.5));
                assertSame(instance, entity.onAfterInsert(instance, (ResultSet) null));
                parameters.clear();
                assertSame(instance, entity.onInsert(instance, statement(parameters)));
                assertEquals(12, parameters.size());
                assertEquals(0, parameters.get(4));
                assertNull(parameters.get(7));
            }
        }
        assertOnlyHoldersGenerated();
        assertHoldersUseDirectCallbacks();
    }

    @Test
    void absentLogicalOperationsUseSharedDefaults() throws Exception {
        compile(Map.of("demo.KeyOnly", """
                package demo;
                import io.yupiik.fusion.framework.build.api.persistence.*;
                @Table("keys") public record KeyOnly(@Id(autoIncremented=true) Integer id) {}
                """, "demo.NoId", """
                package demo;
                import io.yupiik.fusion.framework.build.api.persistence.*;
                @Table("values") public record NoId(String name) {}
                """), "-Afusion.generateBeanForPersistenceEntities=false", "-Afusion.generateModule=false");
        try (final var loader = loader()) {
            final var keyFactory = GeneratedFactories.invoke(loader, "demo", "KeyOnly$FusionPersistenceEntity", Entity.Factory.class);
            final var keys = keyFactory.create(configuration(), null);
            final var key = keys.mapper(List.of("id")).apply(row(7));
            assertSame(key, keys.onInsert(key, null));
            assertEquals("KeyOnly[id=8]", keys.onAfterInsert(key, row(8)).toString());
            final var valueFactory = GeneratedFactories.invoke(loader, "demo", "NoId$FusionPersistenceEntity", Entity.Factory.class);
            final var values = valueFactory.create(configuration(), null);
            final var value = values.mapper(List.of("name")).apply(row("value"));
            values.onDelete(value, null);
            values.onFindById(List.of(), null);
            assertSame(value, values.onAfterInsert(value, (ResultSet) null));
        }
        final var sources = new StringBuilder();
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            for (final var file : files.filter(it -> it.toString().endsWith(".java")).toList()) {
                sources.append(Files.readString(file));
            }
        }
        final var source = sources.toString();
        assertTrue(source.contains("null, null, null"), source);
        assertFalse(source.contains(".SqlException"), source);
        assertFalse(source.contains("implements io.yupiik.fusion.framework.api.spi.dispatch.Dispatch"), source);
        assertFalse(source.contains("private static void"), source);
    }

    @Test
    void standaloneTypedOperationsProjectionsKeysCallbacksAndCheckedErrors() throws Exception {
        compile(Map.of("demo.Model", """
                package demo;
                import io.yupiik.fusion.framework.build.api.persistence.*;
                @Table("models")
                public record Model(@Id(autoIncremented=true) Integer id, String name, Detail detail) {
                    @OnInsert Model insert() { return new Model(id, name + "-insert", detail); }
                    @OnUpdate Model update() { return new Model(id, name + "-update", detail); }
                    @OnDelete void delete() {}
                    @OnLoad Model load() { return this; }
                    @Table(Table.EMBEDDABLE)
                    public record Detail(java.time.LocalDate day, Integer number) {}
                }
                """), "-Afusion.generateBeanForPersistenceEntities=false", "-Afusion.generateModule=false");
        try (final var loader = loader()) {
            final var factory = GeneratedFactories.invoke(loader, "demo", "Model$FusionPersistenceEntity", Entity.Factory.class);
            final var configuration = configuration();
            final var entity = factory.create(configuration, null);
            assertEquals(loader.loadClass("demo.Model"), entity.getRootType());
            assertEquals("INSERT INTO models (name, day, number) VALUES (?, ?, ?)", entity.getBatchInsertQuery());
            assertEquals("UPDATE models SET name = ?, day = ?, number = ? WHERE id = ?", entity.getUpdateQuery());
            final var model = loader.loadClass("demo.Model");
            final var detail = loader.loadClass("demo.Model$Detail");
            final var nested = detail.getConstructor(LocalDate.class, Integer.class).newInstance(LocalDate.of(2026, 1, 2), 42);
            final var instance = model.getConstructor(Integer.class, String.class, detail).newInstance(null, "value", nested);
            final var parameters = new java.util.LinkedHashMap<Integer, Object>();
            final var statement = statement(parameters);
            final var bound = entity.onInsert(instance, statement);
            assertEquals(Map.of(1, "value-insert", 2, LocalDate.of(2026, 1, 2), 3, 42), parameters);
            final var inserted = entity.onAfterInsert(bound, row(1));
            assertEquals("Model[id=1, name=value-insert, detail=Detail[day=2026-01-02, number=42]]", inserted.toString());
            parameters.clear();
            assertTrue(entity.onUpdate(inserted, statement).toString().contains("value-insert-update"));
            assertEquals(Map.of(1, "value-insert-update", 2, LocalDate.of(2026, 1, 2), 3, 42, 4, 1), parameters);
            parameters.clear();
            entity.onFindById(1, statement);
            assertEquals(Map.of(1, 1), parameters);
            assertTrue(entity.mapper(List.of("id", "name", "day", "number"))
                    .apply(row(1, "value-insert-update", LocalDate.of(2026, 1, 2), 42)).toString().contains("value-insert-update"));
            final var projected = entity.mapFromPrefix("p", List.of("unused", "pnumber", "pname", "pid", "pday"));
            final var missing = entity.mapper(List.of("name"));
            assertEquals("Model[id=17, name=projected, detail=Detail[day=2026-01-03, number=9]]",
                    projected.apply(row(0, 9, "projected", 17, LocalDate.of(2026, 1, 3))).toString());
            // Preserve the legacy boxed native reader's missing-column defaults.
            assertEquals("Model[id=0, name=partial, detail=Detail[day=null, number=0]]", missing.apply(row("partial")).toString());
            final var shiftedConfiguration = configuration();
            shiftedConfiguration.setTranslation(new DefaultTranslation() {
                @Override
                public <T> io.yupiik.fusion.persistence.api.SQLFunction<ResultSet, T> reader(final int index, final Class<T> type) {
                    final var original = super.reader(index, type);
                    return row -> type.cast(((LocalDate) original.apply(row)).plusDays(1));
                }
            });
            final var shifted = factory.create(shiftedConfiguration, null).mapper(List.of("id", "name", "day", "number"));
            assertTrue(shifted.apply(row(1, "shifted", LocalDate.of(2026, 1, 3), 5)).toString().contains("day=2026-01-04"));
            assertTrue(entity.mapper(List.of("id", "name", "day", "number"))
                    .apply(row(1, "shifted", LocalDate.of(2026, 1, 3), 5)).toString().contains("day=2026-01-03"));
            // A missing nullable scalar still uses the native setNull operation.
            parameters.clear();
            entity.onFindById(null, statement);
            assertTrue(parameters.containsKey(1));
            assertNull(parameters.get(1));
            final var failure = new SQLException("deliberate");
            final var failedStatement = (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{java.sql.PreparedStatement.class}, (proxy, method, args) -> { throw failure; });
            assertSame(failure, assertThrows(SQLException.class, () -> entity.onFindById(1, failedStatement)));
            final var failedRow = (ResultSet) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{ResultSet.class}, (proxy, method, args) -> { throw failure; });
            final var mapper = entity.mapper(List.of("id"));
            assertSame(failure, assertThrows(PersistenceException.class, () -> mapper.apply(failedRow)).getCause());
            parameters.clear();
            entity.onDelete(inserted, statement);
            assertEquals(Map.of(1, 1), parameters);
        }
        assertHoldersUseDirectCallbacks();
        assertOnlyHoldersGenerated();
    }

    @Test
    void nullEmbeddableTranslatesNullOnInsertAndUpdate() throws Exception {
        compile(Map.of("demo.Model", """
                package demo;
                import io.yupiik.fusion.framework.build.api.persistence.*;
                @Table("models")
                public record Model(@Id long id, Detail detail) {
                    @Table(Table.EMBEDDABLE)
                    public record Detail(java.time.LocalDate day) {}
                }
                """), "-Afusion.generateBeanForPersistenceEntities=false", "-Afusion.generateModule=false");
        try (final var loader = loader()) {
            final var factory = GeneratedFactories.invoke(loader, "demo", "Model$FusionPersistenceEntity", Entity.Factory.class);
            final var configuration = configuration();
            final var translated = new ArrayList<Object>();
            configuration.setTranslation(new DefaultTranslation() {
                @Override
                public void bind(final java.sql.PreparedStatement statement, final int index, final Object value,
                                 final Class<?> type) throws SQLException {
                    assertEquals(LocalDate.class, type);
                    translated.add(value);
                    super.bind(statement, index, value, type);
                }
            });
            final var entity = factory.create(configuration, null);
            final var instance = loader.loadClass("demo.Model")
                    .getConstructor(long.class, loader.loadClass("demo.Model$Detail")).newInstance(7L, null);
            final var parameters = new java.util.LinkedHashMap<Integer, Object>();
            assertSame(instance, entity.onInsert(instance, statement(parameters)));
            assertEquals(2, parameters.size());
            assertEquals(7L, parameters.get(1));
            assertNull(parameters.get(2));
            parameters.clear();
            assertSame(instance, entity.onUpdate(instance, statement(parameters)));
            assertEquals(2, parameters.size());
            assertNull(parameters.get(1));
            assertEquals(7L, parameters.get(2));
            assertEquals(java.util.Arrays.asList(null, null), translated);
        }
    }

    @Test
    void nullEmbeddableBindsPrimitiveAndEnumNullsOnInsertAndUpdate() throws Exception {
        compile(Map.of("demo.Model", """
                package demo;
                import io.yupiik.fusion.framework.build.api.persistence.*;
                @Table("models")
                public record Model(@Id long id, Detail detail) {
                    public enum State { OPEN }
                    @Table(Table.EMBEDDABLE)
                    public record Detail(int quantity, State state) {}
                }
                """), "-Afusion.generateBeanForPersistenceEntities=false", "-Afusion.generateModule=false");
        try (final var loader = loader()) {
            final var factory = GeneratedFactories.invoke(loader, "demo", "Model$FusionPersistenceEntity", Entity.Factory.class);
            final var entity = factory.create(configuration(), null);
            final var instance = loader.loadClass("demo.Model")
                    .getConstructor(long.class, loader.loadClass("demo.Model$Detail")).newInstance(7L, null);
            final var parameters = new java.util.LinkedHashMap<Integer, Object>();
            assertSame(instance, entity.onInsert(instance, statement(parameters)));
            assertEquals(3, parameters.size());
            assertEquals(7L, parameters.get(1));
            assertNull(parameters.get(2));
            assertNull(parameters.get(3));
            parameters.clear();
            assertSame(instance, entity.onUpdate(instance, statement(parameters)));
            assertEquals(3, parameters.size());
            assertNull(parameters.get(1));
            assertNull(parameters.get(2));
            assertEquals(7L, parameters.get(3));
        }
    }

    @Test
    void bindingsEvaluateAccessorsOnceAndTranslateNullEmbeddedValues() throws Exception {
        compile(Map.of("demo.Model", """
                package demo;
                import io.yupiik.fusion.framework.build.api.persistence.*;
                @Table("models")
                public record Model(@Id String tenant, @Id long id, Integer number, State state, Detail detail) {
                    public static int tenants, numbers, states, details;
                    public String tenant() { tenants++; return tenant; }
                    public Integer number() { numbers++; return number; }
                    public State state() { states++; return state; }
                    public Detail detail() { details++; return detail; }
                    public enum State { OPEN }
                    @Table(Table.EMBEDDABLE)
                    public record Detail(String label, java.time.LocalDate day, int quantity) {
                        public static int labels, days, quantities;
                        public String label() { labels++; return label; }
                        public java.time.LocalDate day() { days++; return day; }
                        public int quantity() { quantities++; return quantity; }
                    }
                }
                """), "-Afusion.generateBeanForPersistenceEntities=false", "-Afusion.generateModule=false");
        try (final var loader = loader()) {
            final var factory = GeneratedFactories.invoke(loader, "demo", "Model$FusionPersistenceEntity", Entity.Factory.class);
            final var configuration = configuration();
            final var translated = new ArrayList<Object>();
            configuration.setTranslation(new DefaultTranslation() {
                @Override
                public void bind(final java.sql.PreparedStatement statement, final int index, final Object value,
                                 final Class<?> type) throws SQLException {
                    assertEquals(LocalDate.class, type);
                    translated.add(value);
                    super.bind(statement, index, value, type);
                }
            });
            final var entity = factory.create(configuration, null);
            final var model = loader.loadClass("demo.Model");
            final var detail = loader.loadClass("demo.Model$Detail");
            final var state = loader.loadClass("demo.Model$State");
            final var constructor = model.getConstructor(String.class, long.class, Integer.class, state, detail);
            final var nested = detail.getConstructor(String.class, LocalDate.class, int.class)
                    .newInstance("label", LocalDate.of(2026, 1, 2), 3);
            final var instance = constructor.newInstance("tenant", 7L, 4, state.getEnumConstants()[0], nested);
            final var parameters = new java.util.LinkedHashMap<Integer, Object>();
            entity.onInsert(instance, statement(parameters));
            assertEquals(Map.of(1, "tenant", 2, 7L, 3, 4, 4, "OPEN", 5, "label", 6, LocalDate.of(2026, 1, 2), 7, 3), parameters);
            for (final var field : List.of("tenants", "numbers", "states", "details")) {
                assertEquals(1, model.getField(field).get(null), field);
            }
            for (final var field : List.of("labels", "days", "quantities")) {
                assertEquals(1, detail.getField(field).get(null), field);
            }
            parameters.clear();
            entity.onUpdate(instance, statement(parameters));
            assertEquals(Map.of(1, 4, 2, "OPEN", 3, "label", 4, LocalDate.of(2026, 1, 2), 5, 3, 6, "tenant", 7, 7L), parameters);
            for (final var field : List.of("tenants", "numbers", "states", "details")) {
                assertEquals(2, model.getField(field).get(null), field);
            }
            final var empty = constructor.newInstance(null, 8L, null, null, null);
            parameters.clear();
            entity.onInsert(empty, statement(parameters));
            assertEquals(7, parameters.size());
            assertEquals(8L, parameters.get(2));
            for (final int index : List.of(1, 3, 4, 5, 6, 7)) {
                assertNull(parameters.get(index));
            }
            assertEquals(java.util.Arrays.asList(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2), null), translated);
            parameters.clear();
            entity.onFindById(java.util.Arrays.asList(null, 9L), statement(parameters));
            assertNull(parameters.get(1));
            assertEquals(9L, parameters.get(2));
        }
        assertOnlyHoldersGenerated();
        assertHoldersUseDirectCallbacks();
    }

    @Test
    void checkedCallbackErrorsKeepIdentityAndCloseInjectedDependenciesInReverseOrder() throws Exception {
        compile(Map.of("demo.Model", """
                package demo;
                import io.yupiik.fusion.framework.build.api.persistence.*;
                @Table("models")
                public record Model(@Id(autoIncremented=true) Integer id, String name) {
                    public static final java.sql.SQLException FAILURE = new java.sql.SQLException("callback");
                    public static String closed = "";
                    public static boolean failClose;
                    @OnInsert Model insert(First $first, Second $second) throws java.sql.SQLException { throw FAILURE; }
                    @OnUpdate Model update(First first, Second second) throws java.sql.SQLException { throw FAILURE; }
                    @OnDelete void delete(First first, Second second) throws java.sql.SQLException { throw FAILURE; }
                    @OnLoad void load(First first, Second second) throws java.sql.SQLException { throw FAILURE; }
                    public static class First implements AutoCloseable {
                        public void close() { closed += "first;"; if (failClose) { throw new IllegalArgumentException("first-close"); } }
                    }
                    public static class Second implements AutoCloseable {
                        public void close() { closed += "second;"; if (failClose) { throw new IllegalArgumentException("second-close"); } }
                    }
                }
                """), "-Afusion.generateModule=false");
        try (final var loader = loader()) {
            final var model = loader.loadClass("demo.Model");
            final var first = loader.loadClass("demo.Model$First");
            final var second = loader.loadClass("demo.Model$Second");
            final var factory = GeneratedFactories.invoke(loader, "demo", "Model$FusionPersistenceEntity", Entity.Factory.class);
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(
                    dependencyBean(first), dependencyBean(second)).start()) {
                final var entity = factory.create(configuration(), container);
                final var instance = model.getConstructor(Integer.class, String.class).newInstance(1, "value");
                final var failure = (SQLException) model.getField("FAILURE").get(null);
                final var statement = statement(new java.util.LinkedHashMap<>());
                assertSame(failure, assertThrows(SQLException.class, () -> entity.onInsert(instance, statement)));
                assertSame(failure, assertThrows(SQLException.class, () -> entity.onUpdate(instance, statement)));
                assertSame(failure, assertThrows(SQLException.class, () -> entity.onDelete(instance, statement)));
                final var mapper = entity.mapper(List.of("id", "name"));
                assertSame(failure, assertThrows(PersistenceException.class, () -> mapper.apply(row(1, "value"))).getCause());
                assertEquals("second;first;".repeat(4), model.getField("closed").get(null));
                final var failedRow = (ResultSet) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{ResultSet.class}, (proxy, method, args) -> { throw failure; });
                assertSame(failure, assertThrows(SQLException.class, () -> entity.onAfterInsert(instance, failedRow)));
                model.getField("failClose").setBoolean(null, true);
                assertSame(failure, assertThrows(SQLException.class, () -> entity.onDelete(instance, statement)));
                assertEquals(List.of("second-close", "first-close"),
                        java.util.Arrays.stream(failure.getSuppressed()).map(Throwable::getCause).map(Throwable::getMessage).toList());
                assertEquals("second;first;".repeat(5), model.getField("closed").get(null));
            }
        }
        assertOnlyHoldersGenerated();
        assertHoldersUseDirectCallbacks();
    }

    private Object newDependency(final Class<?> type) {
        try {
            return type.getConstructor().newInstance();
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private ProvidedInstanceBean<?> dependencyBean(final Class<?> type) {
        return new ProvidedInstanceBean(DefaultScoped.class, type, () -> newDependency(type)) {
            @Override
            public void destroy(final RuntimeContainer container, final Object instance) {
                try {
                    ((AutoCloseable) instance).close();
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    @Test
    void optionalBeanUsesEntityGenericTypeAndClosesLifecycleLookups() throws Exception {
        compile(Map.of("demo.Model", """
                package demo;
                import io.yupiik.fusion.framework.build.api.persistence.*;
                @Table("models")
                public record Model(@Id String tenant, @Id int id, String name) {
                    @OnInsert Model insert(Dependency dependency) { return new Model(tenant, id, name + dependency.value()); }
                    @OnUpdate Model update(Dependency dependency) { return new Model(tenant, id, name + dependency.value()); }
                    @OnDelete void delete(Dependency dependency) { dependency.value(); }
                    @OnLoad void load(Dependency dependency) { dependency.value(); }
                    public static class Dependency implements AutoCloseable {
                        public static int closed;
                        public String value() { return "-dependency"; }
                        public void close() { closed++; }
                    }
                }
                """));
        try (final var loader = loader()) {
            final var model = loader.loadClass("demo.Model");
            final var bean = GeneratedFactories.bean(loader, new Types.ParameterizedTypeImpl(Entity.class, model,
                    new Types.ParameterizedTypeImpl(List.class, Object.class)));
            assertEquals(new Types.ParameterizedTypeImpl(Entity.class, model,
                    new Types.ParameterizedTypeImpl(List.class, Object.class)), bean.type());
            final var dependency = loader.loadClass("demo.Model$Dependency");
            final var configuration = configuration();
            try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(bean,
                    new ProvidedInstanceBean<>(DefaultScoped.class, DatabaseConfiguration.class, () -> configuration),
                    new ProvidedInstanceBean(DefaultScoped.class, dependency, () -> {
                        try { return dependency.getConstructor().newInstance(); } catch (final Exception e) { throw new IllegalStateException(e); }
                    }) {
                        @Override
                        public void destroy(final io.yupiik.fusion.framework.api.RuntimeContainer container, final Object instance) {
                            try {
                                ((AutoCloseable) instance).close();
                            } catch (final Exception e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    }).start();
                 final var entityInstance = container.lookup(bean.type())) {
                final var entity = (FunctionalEntity) entityInstance.instance();
                assertEquals("SELECT tenant, id, name FROM models WHERE tenant = ? AND id = ?", entity.getFindByIdQuery());
                final var instance = model.getConstructor(String.class, int.class, String.class).newInstance("tenant", 3, "name");
                final var parameters = new java.util.LinkedHashMap<Integer, Object>();
                final var statement = statement(parameters);
                assertTrue(entity.onInsert(instance, statement).toString().contains("name-dependency"));
                assertEquals(Map.of(1, "tenant", 2, 3, 3, "name-dependency"), parameters);
                parameters.clear();
                entity.onFindById(List.of("tenant", 3), statement);
                assertEquals(Map.of(1, "tenant", 2, 3), parameters);
                assertTrue(entity.mapper(List.of("tenant", "id", "name"))
                        .apply(row("tenant", 3, "name-dependency")).toString().contains("name-dependency"));
                parameters.clear();
                entity.onUpdate(instance, statement);
                assertEquals(Map.of(1, "name-dependency", 2, "tenant", 3, 3), parameters);
                parameters.clear();
                entity.onDelete(instance, statement);
                assertEquals(Map.of(1, "tenant", 2, 3), parameters);
                assertEquals(4, dependency.getField("closed").get(null));
            }
        }
        assertHoldersUseDirectCallbacks();
        assertOnlyHoldersGenerated();
    }

    @Test
    void constructorOrderEnumsNullableScalarsAndTranslatedGenericProperties() throws Exception {
        compile(Map.of("demo.Model", """
                package demo;
                import io.yupiik.fusion.framework.build.api.persistence.*;
                @Table("models")
                public record Model(String name, @Id long id, @Column(name="labels") java.util.List<String> tags, Status status, Short count) {
                    public enum Status { ACTIVE, INACTIVE }
                }
                """), "-Afusion.generateBeanForPersistenceEntities=false");
        try (final var loader = loader()) {
            final var factory = GeneratedFactories.invoke(loader, "demo", "Model$FusionPersistenceEntity", Entity.Factory.class);
            final var translations = new java.util.concurrent.atomic.AtomicInteger();
            final var configuration = configuration();
            configuration.setTranslation(new DefaultTranslation() {
                @Override
                public <T> io.yupiik.fusion.persistence.api.SQLFunction<ResultSet, T> reader(final int index, final Class<T> type) {
                    if (type == List.class) {
                        translations.incrementAndGet();
                        return row -> type.cast(row.getObject(index + 1));
                    }
                    return super.reader(index, type);
                }
            });
            final var entity = factory.create(configuration, null);
            assertEquals("INSERT INTO models (id, name, labels, status, count) VALUES (?, ?, ?, ?, ?)", entity.getBatchInsertQuery());
            final var model = loader.loadClass("demo.Model");
            final var status = loader.loadClass("demo.Model$Status");
            final var active = status.getField("ACTIVE").get(null);
            final var constructor = model.getConstructor(String.class, long.class, List.class, status, Short.class);
            final var instance = constructor.newInstance("name", 7L, List.of("one"), active, (short) 2);
            final var parameters = new java.util.LinkedHashMap<Integer, Object>();
            entity.onInsert(instance, statement(parameters));
            assertEquals(Map.of(1, 7L, 2, "name", 3, List.of("one"), 4, "ACTIVE", 5, (short) 2), parameters);
            final var mapper = entity.mapper(List.of("labels", "count", "id", "status", "name"));
            assertEquals(1, translations.get());
            assertEquals(instance, mapper.apply(row(List.of("one"), (short) 2, 7L, "ACTIVE", "name")));
            assertEquals(instance, mapper.apply(row(List.of("one"), (short) 2, 7L, "ACTIVE", "name")));
            assertEquals(1, translations.get());
            parameters.clear();
            entity.onInsert(constructor.newInstance(null, 8L, null, null, null), statement(parameters));
            assertEquals(5, parameters.size());
            assertEquals(8L, parameters.get(1));
            assertNull(parameters.get(2));
            assertNull(parameters.get(3));
            assertNull(parameters.get(4));
            assertNull(parameters.get(5));
        }
        assertHoldersUseDirectCallbacks();
        assertOnlyHoldersGenerated();
    }

    @Test
    void nestedEntitiesInNamedAndDefaultPackagesUseSourceNamesAndStableBinaryIds() throws Exception {
        compile(Map.of("demo.Models", """
                package demo;
                public class Models {
                    @io.yupiik.fusion.framework.build.api.persistence.Table("models")
                    public record Model(@io.yupiik.fusion.framework.build.api.persistence.Id long id, String name) {}
                }
                """, "demo.Model$id", """
                package demo;
                @io.yupiik.fusion.framework.build.api.persistence.Table("dollars")
                public record Model$id(@io.yupiik.fusion.framework.build.api.persistence.Id long id, String name) {}
                """, "DefaultModels", """
                public class DefaultModels {
                    @io.yupiik.fusion.framework.build.api.persistence.Table("defaults")
                    public record Model(@io.yupiik.fusion.framework.build.api.persistence.Id long id, String name) {}
                }
                """), "-Afusion.generateBeanForPersistenceEntities=false", "-Afusion.generateModule=false");
        try (final var loader = loader()) {
            for (final var name : List.of("demo.Models$Model", "DefaultModels$Model", "demo.Model$id")) {
                final var factory = GeneratedFactories.invoke(loader, name.startsWith("demo.") ? "demo" : "", name.substring(name.lastIndexOf('.') + 1) + "$FusionPersistenceEntity", Entity.Factory.class);
                final var entity = factory.create(configuration(), null);
                assertEquals(loader.loadClass(name), entity.getRootType());
                assertEquals(loader.loadClass(name).getSimpleName() + "[id=7, name=value]",
                        entity.mapper(List.of("name", "id")).apply(row("value", 7L)).toString());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void standaloneFactoriesAndBeanReferencesSurviveHolderSplitting(final boolean module) throws Exception {
        final var sources = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < 36; i++) {
            sources.put("demo.Model" + i, "package demo; import io.yupiik.fusion.framework.build.api.persistence.*; " +
                    "@Table(\"models\") public record Model" + i + "(@Id long id, String name) {}");
        }
        compile(sources, "-Afusion.generateModule=" + module);
        try (final var loader = loader()) {
            assertEquals(module ? 36 : 0, GeneratedFactories.beans(loader).size());
            assertTrue(GeneratedFactories.methods(loader, "demo", FunctionalBean.class).isEmpty());
            for (int i = 0; i < 36; i++) {
                final var factory = GeneratedFactories.invoke(loader, "demo", "Model" + i + "$FusionPersistenceEntity", Entity.Factory.class);
                assertEquals(loader.loadClass("demo.Model" + i), factory.create(configuration(), null).getRootType());
                if (!module) {
                    continue;
                }
                final var bean = GeneratedFactories.bean(loader, new Types.ParameterizedTypeImpl(Entity.class,
                        loader.loadClass("demo.Model" + i), Long.class));
                try (final var container = ConfiguringContainer.of().disableAutoDiscovery(true).register(bean,
                        new ProvidedInstanceBean<>(DefaultScoped.class, DatabaseConfiguration.class, this::configuration)).start();
                     final var instance = container.lookup(bean.type())) {
                    assertEquals(loader.loadClass("demo.Model" + i), ((FunctionalEntity<?, ?>) instance.instance()).getRootType());
                }
            }
        }
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            assertTrue(files.count() > 2);
        }
        assertOnlyHoldersGenerated();
        assertHoldersUseDirectCallbacks();
    }

    private java.sql.PreparedStatement statement(final Map<Integer, Object> parameters) {
        return (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{java.sql.PreparedStatement.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("set")) {
                        parameters.put((Integer) args[0], method.getName().equals("setNull") ? null : args[1]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private ResultSet row(final Object... values) {
        return (ResultSet) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("get")) {
                        final var value = values[(Integer) args[0] - 1];
                        if (method.getName().equals("getDate") && value instanceof LocalDate date) {
                            return java.sql.Date.valueOf(date);
                        }
                        return value;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    private DatabaseConfiguration configuration() {
        final var configuration = new DatabaseConfiguration();
        configuration.setTranslation(new DefaultTranslation());
        return configuration;
    }

    private URLClassLoader loader() throws Exception {
        return new URLClassLoader(new URL[]{work.resolve("classes").toUri().toURL()}, getClass().getClassLoader());
    }

    private void assertOnlyHoldersGenerated() throws Exception {
        try (final var files = Files.list(work.resolve("generated/demo"))) {
            for (final var file : files.toList()) {
                final var name = file.getFileName().toString();
                assertFalse(name.contains("$FusionPersistenceEntity") || name.contains("$FusionBean"), name);
                assertFalse(name.contains("$"), "Only shared holders, module and bounded registration siblings are generated: " + name);
            }
        }
        try (final var files = Files.list(work.resolve("classes/demo"))) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().contains("$FusionPersistenceEntity") ||
                    (file.getFileName().toString().startsWith("FusionBindings") && file.getFileName().toString().contains("$"))));
        }
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
        assertFalse(output.toString().contains("io/yupiik/fusion/framework/api/spi/dispatch/Dispatch.invoke"), output.toString());
        assertFalse(output.toString().contains("java/lang/reflect/Method"), output.toString());
    }

    private void compile(final Map<String, String> sources, final String... extra) throws Exception {
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
            final var options = new ArrayList<>(List.of("--release", "17", "-classpath", System.getProperty("java.class.path"),
                    "-d", work.resolve("classes").toString(), "-s", work.resolve("generated").toString(),
                    "-Afusion.workdir=" + work.resolve("state"), "-Afusion.moduleFqn=demo.Module", "-Xlint:all,-processing,-serial", "-Werror"));
            options.addAll(List.of(extra));
            final var task = compiler.getTask(output, manager, null, options, null, manager.getJavaFileObjectsFromPaths(paths));
            task.setProcessors(List.of(new FusionProcessor()));
            assertTrue(task.call(), output.toString());
        }
    }
}
