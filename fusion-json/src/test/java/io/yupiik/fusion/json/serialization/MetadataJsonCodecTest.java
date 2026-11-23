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
package io.yupiik.fusion.json.serialization;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.internal.codec.EnumJsonCodec;
import io.yupiik.fusion.json.internal.codec.ObjectJsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ContainerKind.LIST;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ContainerKind.MAP;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ContainerKind.MAP_LIST;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ContainerKind.SET;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ContainerKind.VALUE;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ValueKind.BOOLEAN;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ValueKind.DOUBLE;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ValueKind.GENERIC_OBJECT;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ValueKind.INTEGER;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ValueKind.LONG;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ValueKind.MODEL;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ValueKind.NUMBER;
import static io.yupiik.fusion.json.serialization.MetadataJsonCodec.ValueKind.STRING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataJsonCodecTest {
    private final MetadataJsonCodec<Node> node = new MetadataJsonCodec<>(Node.class, List.of(
            property(10, "n\"m", 0, VALUE, STRING, true, false, false, String.class),
            property(11, "next", 1, VALUE, MODEL, true, false, false, Node.class)), new int[]{11, 10}, List.of(Node::name, Node::next),
            length -> switch (length) { case 3 -> 0; case 4 -> 1; default -> -1; },
            slots -> new Node((String) slots[0], (Node) slots[1]));
    private final MetadataJsonCodec<Envelope> envelope = new MetadataJsonCodec<>(Envelope.class, List.of(
            property(21, "any", 1, VALUE, GENERIC_OBJECT, true, false, false, Object.class),
            property(20, "root", 0, VALUE, MODEL, true, false, false, Node.class),
            property(22, "items", 2, LIST, MODEL, true, false, false, Node.class),
            property(23, "index", 3, MAP, MODEL, true, false, false, Node.class),
            property(25, "ignored", 5, VALUE, STRING, true, false, true, String.class),
            property(24, null, 4, MAP, GENERIC_OBJECT, true, true, false, Object.class)), new int[]{20, 24, 22, 23, 21},
            Arrays.asList(Envelope::root, Envelope::any, Envelope::items, Envelope::index, Envelope::others, null),
            length -> switch (length) { case 3 -> 0; case 4 -> 1; case 5 -> 2; default -> -1; },
            MetadataJsonCodecTest::createEnvelope);

    @Test
    void enumConversionUsesDirectCallback() {
        final var codec = new EnumJsonCodec<>(Mode.class, List.of(Mode.YES, Mode.NO),
                Mode::toJsonString);
        try (final var mapper = mapper(codec)) {
            assertEquals("\"yes\"", mapper.toString(Mode.YES));
            assertEquals(Mode.NO, mapper.fromString(Mode.class, "\"no\""));
            assertThrows(IllegalArgumentException.class, () -> mapper.fromString(Mode.class, "\"NO\""));
        }
    }

    @Test
    void directCallbacksRejectMissingActiveAccessors() {
        final var properties = List.of(property(0, "name", 0, VALUE, STRING, true, false, false, String.class));
        assertThrows(IllegalArgumentException.class, () -> new MetadataJsonCodec<>(Node.class,
                properties, new int[]{0}, List.of(), length -> 0, slots -> new Node((String) slots[0], null)));
        assertThrows(NullPointerException.class, () -> new MetadataJsonCodec<>(Node.class,
                properties, new int[]{0}, Arrays.asList((Function<Node, Object>) null),
                length -> 0, slots -> new Node((String) slots[0], null)));
    }

    @Test
    void directCallbacksRoundTripWithDistinctReadWriteAndConstructorOrders() {
        try (final var mapper = mapper(node, envelope)) {
            final var leaf = new Node("leaf", null);
            final var value = new Envelope(new Node("parent", leaf), Map.of("a", List.of(true, new BigDecimal("2"))),
                    List.of(leaf), Map.of("child", leaf), Map.of("extension", "extra"), null);
            final var json = "{\"root\":{\"next\":{\"n\\\"m\":\"leaf\"},\"n\\\"m\":\"parent\"},\"extension\":\"extra\"," +
                    "\"items\":[{\"n\\\"m\":\"leaf\"}],\"index\":{\"child\":{\"n\\\"m\":\"leaf\"}},\"any\":{\"a\":[true,2]}}";
            assertEquals(json, mapper.toString(value));
            assertEquals(value, mapper.fromString(Envelope.class, json));
            assertNull(mapper.fromString(Node.class, "null"));
            assertEquals("null", mapper.toString(null));
        }
    }

    @Test
    void unknownKeysRemainOthersButIgnoredKeysAreSkippedAndEmptyOthersIsNotNull() {
        try (final var mapper = mapper(node, envelope)) {
            final var empty = mapper.fromString(Envelope.class, "{}");
            assertEquals(Map.of(), empty.others());
            assertEquals("{}", mapper.toString(empty));
            final var value = mapper.fromString(Envelope.class,
                    "{\"ignored\":\"not a constructor value\",\"nullValue\":null,\"nested\":[{\"x\":1}]}");
            assertNull(value.ignored());
            assertEquals(2, value.others().size());
            assertEquals(List.of(Map.of("x", BigDecimal.ONE)), value.others().get("nested"));
            assertEquals("{\"nullValue\":null,\"nested\":[{\"x\":1}]}", mapper.toString(value));
        }
    }

    @Test
    void primitiveDefaultsIgnoredFieldsAndExplicitNullKeepExistingSemantics() {
        final var properties = new ArrayList<MetadataJsonCodec.Property>();
        properties.add(property(0, "flag", 0, VALUE, BOOLEAN, false, false, false, boolean.class));
        properties.add(property(4, "boxed", 4, VALUE, INTEGER, true, false, false, Integer.class));
        properties.add(property(1, "count", 1, VALUE, INTEGER, false, false, false, int.class));
        properties.add(property(3, "ratio", 3, VALUE, DOUBLE, false, false, false, double.class));
        properties.add(property(2, "total", 2, VALUE, LONG, false, false, false, long.class));
        properties.add(property(5, "ignoredFlag", 5, VALUE, BOOLEAN, false, false, true, boolean.class));
        properties.add(property(6, "ignoredCount", 6, VALUE, INTEGER, false, false, true, int.class));
        properties.add(property(7, "ignoredTotal", 7, VALUE, LONG, false, false, true, long.class));
        properties.add(property(8, "ignoredRatio", 8, VALUE, DOUBLE, false, false, true, double.class));
        properties.add(property(9, "ignoredText", 9, VALUE, STRING, true, false, true, String.class));
        properties.add(property(10, "ignoredList", 10, LIST, STRING, true, false, true, String.class));
        properties.add(property(11, "ignoredMap", 11, MAP, STRING, true, false, true, String.class));
        final var order = new int[]{4, 0, 1, 2, 3};
        final var accessors = new ArrayList<Function<Defaults, Object>>(Arrays.asList(
                Defaults::flag, Defaults::count, Defaults::total, Defaults::ratio, Defaults::boxed,
                null, null, null, null, null, null, null));
        final var defaults = new MetadataJsonCodec<Defaults>(Defaults.class, properties, order,
                accessors,
                length -> switch (length) { case 4 -> 0; case 5 -> 1; default -> -1; }, MetadataJsonCodecTest::createDefaults);
        // Caller-owned metadata containers are not retained.
        properties.clear();
        order[0] = -999;
        accessors.clear();
        try (final var mapper = mapper(defaults)) {
            final var empty = new Defaults(false, 0, 0L, 0.0, null, false, 0, 0L, 0.0, null, null, null);
            assertEquals(empty, mapper.fromString(Defaults.class, "{}"));
            assertEquals(empty, mapper.fromString(Defaults.class, "{\"ignoredFlag\":true,\"ignoredCount\":9," +
                    "\"ignoredTotal\":9,\"ignoredRatio\":9,\"ignoredText\":\"value\",\"ignoredList\":[\"x\"],\"ignoredMap\":{\"a\":\"b\"}}"));
            assertEquals("{\"flag\":false,\"count\":0,\"total\":0,\"ratio\":0.0}", mapper.toString(empty));
            try (final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
                for (final var current : List.of(mapper, legacy)) {
                    assertEquals(empty, current.fromString(Defaults.class,
                            "{\"flag\":true,\"flag\":null,\"count\":42,\"count\":null,\"total\":43,\"total\":null," +
                                    "\"ratio\":1.5,\"ratio\":null,\"boxed\":42,\"boxed\":null}"));
                    for (final var key : List.of("flag", "count", "total", "ratio")) {
                        assertEquals(empty, current.fromString(Defaults.class, "{\"" + key + "\":null}"));
                    }
                }
            }
            final var populated = new Defaults(true, 3, Long.MIN_VALUE, 1.5, 42, false, 0, 0L, 0.0, null, null, null);
            assertEquals(populated, mapper.fromString(Defaults.class, mapper.toString(populated)));
            assertNull(mapper.fromString(Defaults.class, "{\"boxed\":null}").boxed());
        }
    }

    @Test
    void mapperNullConfigurationAndOthersFlattening() {
        try (final var mapper = mapper(node, envelope);
             final var withNulls = mapper.as(JsonMapper.Configuring.class).orElseThrow().serializeNulls().build()) {
            final var value = new Envelope(null, null, null, null, Map.of(), "must not be accessed");
            assertEquals("{}", mapper.toString(value));
            assertEquals("{}", mapper.toString(new Envelope(null, null, null, null, null, null)));
            assertEquals("{\"root\":null,\"items\":null,\"index\":null,\"any\":null}", withNulls.toString(value));
            assertEquals(value.others(), withNulls.fromString(Envelope.class, withNulls.toString(value)).others());
        }
    }

    @Test
    void sharedCodecResolvesDelegatesFromEachMapper() {
        try (final var first = mapper(envelope, new TaggedNodeCodec("first"));
             final var second = mapper(envelope, new TaggedNodeCodec("second"))) {
            final var value = new Envelope(new Node("unused", null), null, null, null, Map.of(), null);
            assertEquals("{\"root\":{\"tag\":\"first\"}}", first.toString(value));
            assertEquals("{\"root\":{\"tag\":\"second\"}}", second.toString(value));
            assertEquals(new Node("first", null), first.fromString(Envelope.class, "{\"root\":{}}").root());
            assertEquals(new Node("second", null), second.fromString(Envelope.class, "{\"root\":{}}").root());
            assertEquals("{\"root\":{\"tag\":\"first\"}}", first.toString(value));
        }
    }

    @Test
    void genericDelegateIsAlsoResolvedFromEachMapper() {
        try (final var first = mapper(envelope, new TaggedObjectCodec("first"));
             final var second = mapper(envelope, new TaggedObjectCodec("second"))) {
            final var value = new Envelope(null, Map.of("input", true), null, null, Map.of(), null);
            assertEquals("{\"any\":{\"tag\":\"first\"}}", first.toString(value));
            assertEquals("{\"any\":{\"tag\":\"second\"}}", second.toString(value));
            assertEquals(Map.of("tag", "first"), first.fromString(Envelope.class, "{\"any\":{}}").any());
            assertEquals(Map.of("tag", "second"), second.fromString(Envelope.class, "{\"any\":{}}").any());
        }
    }

    @Test
    void duplicateKeysUpdateOnlyTheirOwnConstructorSlotsAndOthersEntries() {
        try (final var mapper = mapper(node, envelope)) {
            assertEquals(new Node("last", null), mapper.fromString(Node.class,
                    "{\"n\\\"m\":\"first\",\"next\":{\"n\\\"m\":\"nested\"},\"n\\\"m\":\"last\",\"next\":null}"));
            final var value = mapper.fromString(Envelope.class,
                    "{\"extension\":1,\"ignored\":{\"old\":true},\"extension\":2,\"ignored\":null}");
            assertNull(value.ignored());
            assertEquals(new BigDecimal("2"), value.others().get("extension"));
            assertEquals(1, value.others().size());
            assertEquals("{\"extension\":2}", mapper.toString(value));
        }
    }

    @Test
    void validationAndUnknownAndDuplicatePoliciesUseInvocationContext() {
        try (final var mapper = mapper(node, envelope);
             final var strict = mapper.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectUnknownFields(true).rejectDuplicateKeys(true).build();
             final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectIncompatibleValues(false).build()) {
            for (final var json : List.of("{\"n\\\"m\":1}", "{\"next\":[]}", "{\"next\":false}")) {
                assertThrows(JsonException.class, () -> mapper.fromString(Node.class, json));
            }
            assertEquals(new Node(null, null), legacy.fromString(Node.class, "{\"n\\\"m\":1,\"next\":[]}"));
            assertThrows(JsonException.class, () -> strict.fromString(Node.class, "{\"unknown\":null}"));
            assertThrows(JsonException.class, () -> strict.fromString(Node.class, "{\"next\":null,\"next\":null}"));
            for (final var ignored : List.of("null", "42", "{\"nested\":[{}]}", "[{\"nested\":[]}]")) {
                final var value = strict.fromString(Envelope.class, "{\"extension\":1,\"ignored\":" + ignored + ",\"any\":true}");
                assertNull(value.ignored());
                assertEquals(Map.of("extension", BigDecimal.ONE), value.others());
                assertEquals(true, value.any());
            }
            assertThrows(JsonException.class, () -> strict.fromString(Envelope.class, "{\"extension\":1,\"extension\":2}"));
        }
    }

    @Test
    void genericNumberRetainsAlwaysTypedValidationAndNullSemantics() {
        final var codec = new MetadataJsonCodec<Numeric>(Numeric.class, List.of(
                property(0, "value", 0, VALUE, NUMBER, true, false, false, Number.class)),
                new int[]{0}, List.of(Numeric::value), length -> length == 5 ? 0 : -1,
                slots -> new Numeric((Number) slots[0]));
        try (final var mapper = mapper(codec);
             final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
            for (final var current : List.of(mapper, legacy)) {
                assertEquals(new Numeric(new BigDecimal("1.25")), current.fromString(Numeric.class, "{\"value\":1.25}"));
                assertEquals("{\"value\":1.25}", current.toString(new Numeric(new BigDecimal("1.25"))));
                assertNull(current.fromString(Numeric.class, "{\"value\":1,\"value\":null}").value());
                for (final var value : List.of("\"1\"", "true", "{}", "[]")) {
                    assertThrows(JsonException.class, () -> current.fromString(Numeric.class, "{\"value\":" + value + "}"));
                }
            }
        }
    }

    @Test
    void containersPreserveNullElementsValuesAndExplicitNullFields() {
        final var codec = new MetadataJsonCodec<Containers>(Containers.class, List.of(
                property(0, "set", 0, SET, MODEL, false, false, false, Node.class),
                property(1, "lists", 1, MAP_LIST, MODEL, false, false, false, Node.class)),
                new int[]{0, 1}, List.of(Containers::set, Containers::lists),
                length -> switch (length) { case 3 -> 0; case 5 -> 1; default -> -1; },
                MetadataJsonCodecTest::createContainers);
        try (final var mapper = mapper(node, envelope, codec);
             final var legacy = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectIncompatibleValues(false).build()) {
            for (final var current : List.of(mapper, legacy)) {
                final var value = current.fromString(Envelope.class,
                        "{\"root\":null,\"items\":[null,{}],\"index\":{\"nil\":null,\"node\":{}}}");
                assertNull(value.root());
                assertEquals(Arrays.asList(null, new Node(null, null)), value.items());
                assertEquals(2, value.index().size());
                assertNull(value.index().get("nil"));
                assertEquals(new Node(null, null), value.index().get("node"));
                assertEquals(value, current.fromString(Envelope.class, current.toString(value)));
                final var containers = current.fromString(Containers.class,
                        "{\"set\":[null,{}],\"lists\":{\"nil\":null,\"nodes\":[null,{}]}}");
                assertEquals(2, containers.set().size());
                assertTrue(containers.set().contains(null));
                assertEquals(2, containers.lists().size());
                assertNull(containers.lists().get("nil"));
                assertEquals(Arrays.asList(null, new Node(null, null)), containers.lists().get("nodes"));
                assertEquals(new Containers(null, null), current.fromString(Containers.class,
                        "{\"set\":[{}],\"set\":null,\"lists\":{},\"lists\":null}"));
                final var cleared = current.fromString(Envelope.class,
                        "{\"root\":{},\"root\":null,\"items\":[{}],\"items\":null,\"index\":{},\"index\":null}");
                assertNull(cleared.root());
                assertNull(cleared.items());
                assertNull(cleared.index());
            }
            assertThrows(JsonException.class, () -> mapper.fromString(Containers.class, "{\"set\":{}}"));
            assertThrows(JsonException.class, () -> mapper.fromString(Containers.class, "{\"lists\":[]}"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Containers createContainers(final Object[] slots) {
        return new Containers((Set<Node>) slots[0], (Map<String, List<Node>>) slots[1]);
    }

    private record Containers(Set<Node> set, Map<String, List<Node>> lists) {
    }

    private record Numeric(Number value) {
    }

    @Test
    void invalidMetadataOrdersAndConstructorSlotsFailAtConstruction() {
        final var first = property(10, "name", 0, VALUE, STRING, true, false, false, String.class);
        final var second = property(20, "next", 1, VALUE, MODEL, true, false, false, Node.class);
        final List<Function<Node, Object>> accessors = List.of(Node::name, Node::next);
        final Function<Object[], Node> factory = slots -> new Node((String) slots[0], (Node) slots[1]);
        for (final var order : List.of(new int[]{10}, new int[]{10, 10}, new int[]{10, -1})) {
            assertThrows(IllegalArgumentException.class, () -> new MetadataJsonCodec<>(Node.class,
                    List.of(first, second), order, accessors, length -> 0, factory));
        }
        for (final var invalid : List.of(
                property(20, "next", 0, VALUE, MODEL, true, false, false, Node.class),
                property(20, "next", 2, VALUE, MODEL, true, false, false, Node.class),
                property(10, "next", 1, VALUE, MODEL, true, false, false, Node.class),
                property(20, "x", 1, VALUE, MODEL, true, false, false, Node.class))) {
            assertThrows(IllegalArgumentException.class, () -> new MetadataJsonCodec<>(Node.class,
                    List.of(first, invalid), new int[]{10, 20}, accessors, length -> 0, factory));
        }
    }

    @Test
    void ignoredCallbacksAreNeverInvokedEvenWhenSupplied() {
        final Function<Node, Object> forbidden = value -> {
            throw new AssertionError("Ignored accessor invoked");
        };
        final var codec = new MetadataJsonCodec<>(Node.class, List.of(
                property(0, "name", 0, VALUE, STRING, true, false, true, String.class),
                property(1, "next", 1, VALUE, MODEL, true, false, true, Node.class)),
                new int[0], List.of(forbidden, forbidden), length -> -1,
                slots -> new Node((String) slots[0], (Node) slots[1]));
        try (final var mapper = mapper(codec);
             final var nulls = mapper.as(JsonMapper.Configuring.class).orElseThrow().serializeNulls().build();
             final var strict = mapper.as(JsonMapper.Configuring.class).orElseThrow().rejectUnknownFields(true).build()) {
            assertEquals(new Node(null, null), mapper.fromString(Node.class, "{\"name\":\"ignored\",\"next\":{\"name\":\"ignored\"}}"));
            assertEquals(new Node(null, null), strict.fromString(Node.class, "{\"name\":[{}],\"next\":{\"unknown\":true}}"));
            assertThrows(JsonException.class, () -> strict.fromString(Node.class, "{\"unknown\":null}"));
            assertEquals("{}", mapper.toString(new Node("secret", new Node("nested", null))));
            assertEquals("{}", nulls.toString(new Node("secret", null)));
        }
    }

    @Test
    void sharedCodecKeepsConcurrentMapperDelegatesAndSlotsIsolated() throws Exception {
        final var executor = Executors.newFixedThreadPool(2);
        final var start = new CountDownLatch(1);
        try (final var first = mapper(envelope, new TaggedNodeCodec("first"), new TaggedObjectCodec("first"));
             final var second = mapper(envelope, new TaggedNodeCodec("second"), new TaggedObjectCodec("second"))) {
            final var tasks = new ArrayList<Callable<Void>>();
            for (final var mapper : List.of(first, second)) {
                final var tag = mapper == first ? "first" : "second";
                tasks.add(() -> {
                    start.await();
                    for (int i = 0; i < 50; i++) {
                        final var decoded = mapper.fromString(Envelope.class,
                                "{\"root\":{},\"items\":[{}],\"index\":{\"key\":{}},\"any\":{}}");
                        assertEquals(new Node(tag, null), decoded.root());
                        assertEquals(List.of(new Node(tag, null)), decoded.items());
                        assertEquals(Map.of("key", new Node(tag, null)), decoded.index());
                        assertEquals(Map.of("tag", tag), decoded.any());
                        assertEquals("{\"root\":{\"tag\":\"" + tag + "\"},\"items\":[{\"tag\":\"" + tag +
                                "\"}],\"index\":{\"key\":{\"tag\":\"" + tag + "\"}},\"any\":{\"tag\":\"" + tag + "\"}}",
                                mapper.toString(decoded));
                    }
                    return null;
                });
            }
            final var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (final var future : futures) {
                future.get();
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private JsonMapperImpl mapper(final JsonCodec<?>... codecs) {
        return new JsonMapperImpl(List.of(codecs), key -> Optional.empty());
    }

    private MetadataJsonCodec.Property property(final int id, final String name, final int slot,
                                                final MetadataJsonCodec.ContainerKind container,
                                                final MetadataJsonCodec.ValueKind kind, final boolean wrapper,
                                                final boolean others, final boolean ignored, final Class<?> delegate) {
        return new MetadataJsonCodec.Property(id, name, slot, container, kind, wrapper, others, ignored, delegate);
    }

    private record Node(String name, Node next) {
    }

    private enum Mode {
        YES, NO;

        public String toJsonString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private record Envelope(Node root, Object any, List<Node> items, Map<String, Node> index,
                            Map<String, Object> others, String ignored) {
    }

    private record Defaults(boolean flag, int count, long total, double ratio, Integer boxed,
                            boolean ignoredFlag, int ignoredCount, long ignoredTotal, double ignoredRatio,
                            String ignoredText, List<String> ignoredList, Map<String, String> ignoredMap) {
    }

    @SuppressWarnings("unchecked")
    private static Envelope createEnvelope(final Object[] slots) {
        return new Envelope((Node) slots[0], slots[1], (List<Node>) slots[2],
                (Map<String, Node>) slots[3], (Map<String, Object>) slots[4], (String) slots[5]);
    }

    @SuppressWarnings("unchecked")
    private static Defaults createDefaults(final Object[] slots) {
        return new Defaults((boolean) slots[0], (int) slots[1], (long) slots[2], (double) slots[3],
                (Integer) slots[4], (boolean) slots[5], (int) slots[6], (long) slots[7], (double) slots[8],
                (String) slots[9], (List<String>) slots[10], (Map<String, String>) slots[11]);
    }

    private static final class TaggedObjectCodec implements JsonCodec<Object> {
        private final ObjectJsonCodec delegate = new ObjectJsonCodec();
        private final String tag;

        private TaggedObjectCodec(final String tag) {
            this.tag = tag;
        }

        @Override
        public Type type() {
            return Object.class;
        }

        @Override
        public Object read(final DeserializationContext context) throws IOException {
            delegate.read(context);
            return Map.of("tag", tag);
        }

        @Override
        public void write(final Object value, final SerializationContext context) throws IOException {
            delegate.write(Map.of("tag", tag), context);
        }
    }

    private record TaggedNodeCodec(String tag) implements JsonCodec<Node> {
        @Override
        public Type type() {
            return Node.class;
        }

        @Override
        public Node read(final DeserializationContext context) throws IOException {
            context.codec(Object.class).read(context);
            return new Node(tag, null);
        }

        @Override
        public void write(final Node value, final SerializationContext context) throws IOException {
            context.codec(Object.class).write(Map.of("tag", tag), context);
        }
    }
}
