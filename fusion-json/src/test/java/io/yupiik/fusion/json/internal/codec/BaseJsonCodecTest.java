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
package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.ExtendedWriter;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BaseJsonCodecTest {
    private final Codec codec = new Codec();

    @Test
    void separatorHandling() throws IOException {
        assertEquals("a:1", write((first, ctx) -> {
            final var next = codec.writeValue(first, "a:".toCharArray(), "1", ctx);
            assertFalse(next);
            return next;
        }, false));
        assertEquals("a:1,b:2", write((first, ctx) -> codec.writeValue(
                codec.writeValue(first, "a:".toCharArray(), "1", ctx), "b:".toCharArray(), "2", ctx), false));
    }

    @Test
    void nullAttributeRespectsNeedsNull() throws IOException {
        assertEquals("", write((first, ctx) -> codec.writeNullAttribute(first, "\"a\":".toCharArray(), ctx), false));
        assertEquals("\"a\":null", write((first, ctx) -> codec.writeNullAttribute(first, "\"a\":".toCharArray(), ctx), true));
        // a skipped attribute does not consume the separator
        assertEquals("\"b\":1", write((first, ctx) -> codec.writeValue(
                codec.writeNullAttribute(first, "\"a\":".toCharArray(), ctx), "\"b\":".toCharArray(), "1", ctx), false));
    }

    @Test
    void nullableAndString() throws IOException {
        assertEquals("\"a\":12", write((first, ctx) -> codec.writeNullable(first, "\"a\":".toCharArray(), 12, ctx), false));
        assertEquals("", write((first, ctx) -> codec.writeNullable(first, "\"a\":".toCharArray(), null, ctx), false));
        assertEquals("\"a\":\"va\\\"l\"", write((first, ctx) -> codec.writeString(first, "\"a\":".toCharArray(), "va\"l", ctx), false));
        assertEquals("\"a\":null", write((first, ctx) -> codec.writeString(first, "\"a\":".toCharArray(), null, ctx), true));
    }

    @Test
    void withCodec() throws IOException {
        assertEquals("\"a\":\"10\"", write((first, ctx) -> codec.writeWithCodec(
                first, "\"a\":".toCharArray(), new BigDecimal("10"), BigDecimal.class, ctx), false));
        assertEquals("", write((first, ctx) -> codec.writeWithCodec(
                first, "\"a\":".toCharArray(), null, BigDecimal.class, ctx), false));
    }

    @Test
    void collections() throws IOException {
        assertEquals("\"a\":[1,2,null]", write((first, ctx) -> codec.writeRawCollection(
                first, "\"a\":".toCharArray(), Arrays.asList(1, 2, null), ctx), false));
        assertEquals("\"a\":[\"x\",null,\"y\"]", write((first, ctx) -> codec.writeStringCollection(
                first, "\"a\":".toCharArray(), Arrays.asList("x", null, "y"), ctx), false));
        assertEquals("\"a\":[\"10\",null]", write((first, ctx) -> codec.writeCollection(
                first, "\"a\":".toCharArray(), Arrays.asList(new BigDecimal("10"), null), BigDecimal.class, ctx), false));
    }

    @Test
    void maps() throws IOException {
        final var withNull = new LinkedHashMap<String, String>();
        withNull.put("x", "1");
        withNull.put("y", null);
        assertEquals("\"a\":{\"x\":\"1\",\"y\":null}", write((first, ctx) -> codec.writeStringMap(
                first, "\"a\":".toCharArray(), withNull, ctx), false));
        assertEquals("\"a\":{\"x\":1}", write((first, ctx) -> codec.writeRawMap(
                first, "\"a\":".toCharArray(), Map.of("x", 1), ctx), false));
        assertEquals("\"a\":{\"x\":\"10\"}", write((first, ctx) -> codec.writeMapWithCodec(
                first, "\"a\":".toCharArray(), Map.of("x", new BigDecimal("10")), BigDecimal.class, ctx), false));
    }

    @Test
    void mapLists() throws IOException {
        final var value = new LinkedHashMap<String, List<String>>();
        value.put("x", List.of("1"));
        value.put("skipped", null); // null entries are skipped
        value.put("y", Arrays.asList("2", null));
        assertEquals("\"a\":{\"x\":[\"1\"],\"y\":[\"2\",null]}", write((first, ctx) -> codec.writeStringMapList(
                first, "\"a\":".toCharArray(), value, ctx), false));
        assertEquals("\"a\":{\"x\":[\"10\"]}", write((first, ctx) -> codec.writeMapListWithCodec(
                first, "\"a\":".toCharArray(), Map.of("x", List.of(new BigDecimal("10"))), BigDecimal.class, ctx), false));
        assertEquals("\"a\":{\"x\":[1]}", write((first, ctx) -> codec.writeRawMapList(
                first, "\"a\":".toCharArray(), Map.of("x", List.of(1)), ctx), false));
    }

    @Test
    void jsonOthersKeepsItsQuirks() throws IOException {
        // flattened, no attribute name
        assertEquals("\"k\":\"v\"", write((first, ctx) -> codec.writeJsonOthers(
                first, "\"a\":".toCharArray(), Map.of("k", "v"), ctx), false));
        assertEquals("\"b\":1", write((first, ctx) -> codec.writeJsonOthers(
                codec.writeValue(first, "\"b\":".toCharArray(), "1", ctx), "\"a\":".toCharArray(), Map.of(), ctx), false));
        assertEquals("\"a\":null", write((first, ctx) -> codec.writeJsonOthers(
                first, "\"a\":".toCharArray(), null, ctx), true));
    }

    @Test
    void jsonOthersSkipsNullValuedEntriesWithoutDanglingComma() throws IOException {
        // trailing null - this is the exact bug: comma was written for "k2" before
        // the loop discovered its value was null and skipped it
        final var trailingNull = new LinkedHashMap<String, Object>();
        trailingNull.put("k1", "v1");
        trailingNull.put("k2", null);
        assertEquals("\"k1\":\"v1\"", write((first, ctx) ->
                codec.writeJsonOthers(first, "\"a\":".toCharArray(), trailingNull, ctx), false));

        // leading null - first entry skipped, comma must not appear before the
        // first *real* entry even though it wasn't the first *map* entry
        final var leadingNull = new LinkedHashMap<String, Object>();
        leadingNull.put("k1", null);
        leadingNull.put("k2", "v2");
        assertEquals("\"k2\":\"v2\"", write((first, ctx) ->
                codec.writeJsonOthers(first, "\"a\":".toCharArray(), leadingNull, ctx), false));

        // null sandwiched between two real entries
        final var middleNull = new LinkedHashMap<String, Object>();
        middleNull.put("k1", "v1");
        middleNull.put("k2", null);
        middleNull.put("k3", "v3");
        assertEquals("\"k1\":\"v1\",\"k3\":\"v3\"", write((first, ctx) ->
                codec.writeJsonOthers(first, "\"a\":".toCharArray(), middleNull, ctx), false));

        // all-null map (non-empty, but every value null) - must behave like empty,
        // not like "wrote nothing but still consumed a comma slot"
        final var allNull = new LinkedHashMap<String, Object>();
        allNull.put("k1", null);
        allNull.put("k2", null);
        assertEquals("\"b\":1", write((first, ctx) -> codec.writeJsonOthers(
                codec.writeValue(first, "\"b\":".toCharArray(), "1", ctx), "\"a\":".toCharArray(), allNull, ctx), false));
    }

    @Test
    void readers() throws IOException {
        assertEquals(List.of("a", "b"), read("[\"a\",\"b\"]", (c, ctx) -> c.readList(ctx, String.class)));
        assertEquals(Set.of("a", "b"), read("[\"a\",\"b\"]", (c, ctx) -> c.readSet(ctx, String.class)));
        assertEquals(Map.of("k", "v"), read("{\"k\":\"v\"}", (c, ctx) -> c.readMap(ctx, String.class)));
        assertEquals(Map.of("k", List.of("v1", "v2")), read("{\"k\":[\"v1\",\"v2\"]}", (c, ctx) -> c.readMapList(ctx, String.class)));
    }

    private String write(final WriteCall call, final boolean serializeNulls) throws IOException {
        final var out = new StringWriter();
        final var context = new JsonCodec.SerializationContext(new ExtendedWriter(out), BaseJsonCodecTest::lookup, serializeNulls);
        call.apply(true, context);
        return out.toString();
    }

    private <T> T read(final String json, final ReadCall<T> call) throws IOException {
        try (final var parser = new JsonParser(new StringReader(json), 16, new BufferProvider(64, -1), true)) {
            return call.apply(codec, new JsonCodec.DeserializationContext(parser, BaseJsonCodecTest::lookup));
        }
    }

    private static JsonCodec<?> lookup(final Class<?> type) {
        if (type == String.class) {
            return new StringJsonCodec();
        }
        if (type == BigDecimal.class) {
            return new BigDecimalJsonCodec();
        }
        if (type == Object.class) {
            return new ObjectJsonCodec();
        }
        throw new IllegalStateException("Unexpected codec lookup: " + type);
    }

    private interface WriteCall {
        boolean apply(boolean first, JsonCodec.SerializationContext context) throws IOException;
    }

    private interface ReadCall<T> {
        T apply(Codec codec, JsonCodec.DeserializationContext context) throws IOException;
    }

    private static class Codec extends BaseJsonCodec<Object> {
        private Codec() {
            super(Object.class);
        }

        @Override
        public Object read(final DeserializationContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(final Object value, final SerializationContext context) {
            throw new UnsupportedOperationException();
        }
    }
}
