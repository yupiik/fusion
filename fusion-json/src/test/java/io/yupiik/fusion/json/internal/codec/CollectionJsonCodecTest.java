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

import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.ExtendedWriter;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectionJsonCodecTest {
    @Test
    void dynamicReadPreservesNullsAndMixedValues() throws IOException {
        final var codec = new CollectionJsonCodec<Object, List<Object>>(null, List.class, ArrayList::new);
        try (final var parser = new JsonParser(new StringReader("[null,1,\"a\",[true,null]]"),
                16, new BufferProvider(16, -1), true)) {
            final var context = new JsonCodec.DeserializationContext(parser, type -> new ObjectJsonCodec());
            assertEquals(Arrays.asList(null, BigDecimal.ONE, "a", Arrays.asList(true, null)), codec.read(context));
        }
    }

    @Test
    void typedReadPreservesNullsWithoutDelegatingThem() throws IOException {
        final var codec = new CollectionJsonCodec<String, List<String>>(new StringJsonCodec(), List.class, ArrayList::new);
        try (final var parser = new JsonParser(new StringReader("[null,\"a\",null]"),
                16, new BufferProvider(16, -1), true)) {
            assertEquals(Arrays.asList(null, "a", null), codec.read(new JsonCodec.DeserializationContext(parser, type -> {
                throw new AssertionError("Typed collections must not look up a codec");
            })));
        }
        try (final var parser = new JsonParser(new StringReader("[null,1]"),
                16, new BufferProvider(16, -1), true)) {
            assertThrows(JsonException.class, () -> codec.read(new JsonCodec.DeserializationContext(parser, type -> null)));
        }
    }

    @Test
    void cachesAcrossNullsAndRefreshesOnTypeChanges() throws IOException {
        final var codec = new CollectionJsonCodec<Object, List<Object>>(null, List.class, ArrayList::new);
        final var lookups = new ArrayList<Class<?>>();
        final var output = new StringWriter();
        codec.write(Arrays.asList(null, "a", null, "b", true, false, null, "c"),
                new JsonCodec.SerializationContext(new ExtendedWriter(output), type -> {
                    lookups.add(type);
                    return type == String.class ? new StringJsonCodec() : new ObjectJsonCodec();
                }, false));
        assertEquals("[null,\"a\",null,\"b\",true,false,null,\"c\"]", output.toString());
        assertEquals(List.of(String.class, Boolean.class, String.class), lookups);
    }

    @Test
    void prefersExactNumberCodecAndFallsBackOnlyForNumbers() throws IOException {
        final var codec = new CollectionJsonCodec<Object, List<Object>>(null, List.class, ArrayList::new);
        final var custom = new JsonCodec<BigInteger>() {
            @Override
            public Type type() {
                return BigInteger.class;
            }

            @Override
            public BigInteger read(final DeserializationContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(final BigInteger value, final SerializationContext context) throws IOException {
                context.writer().write("\"custom\"");
            }
        };
        final var lookups = new ArrayList<Class<?>>();
        final var output = new StringWriter();
        final var context = new JsonCodec.SerializationContext(new ExtendedWriter(output), type -> {
            lookups.add(type);
            if (type == BigInteger.class) {
                return custom;
            }
            return type == Object.class ? new ObjectJsonCodec() : null;
        }, false);
        codec.write(Arrays.asList(Short.valueOf((short) 1), null, Short.valueOf((short) 2),
                BigInteger.TEN, Byte.valueOf((byte) 3)), context);
        assertEquals("[1,null,2,\"custom\",3]", output.toString());
        assertEquals(List.of(Short.class, Object.class, BigInteger.class, Byte.class, Object.class), lookups);
        assertThrows(IllegalStateException.class, () -> codec.write(List.of(new StringBuilder()), context));
    }

    @Test
    void subclassUsesItsOwnCodec() throws IOException {
        final var codec = new CollectionJsonCodec<Object, List<Object>>(null, List.class, ArrayList::new);
        final var parent = namedCodec(Parent.class, "parent");
        final var child = namedCodec(Child.class, "child");
        final var output = new StringWriter();
        codec.write(Arrays.asList(new Parent(), null, new Child(), new Child(), new Parent()),
                new JsonCodec.SerializationContext(new ExtendedWriter(output), type -> {
                    if (type == Parent.class) {
                        return parent;
                    }
                    return type == Child.class ? child : null;
                }, false));
        assertEquals("[\"parent\",null,\"child\",\"child\",\"parent\"]", output.toString());
        assertThrows(IllegalStateException.class, () -> codec.write(List.of(new Parent(), new Child()),
                new JsonCodec.SerializationContext(new ExtendedWriter(new StringWriter()),
                        type -> type == Parent.class ? parent : null, false)));
    }

    @Test
    void typedDelegateRequiresNoLookup() throws IOException {
        final var codec = new CollectionJsonCodec<String, List<String>>(new StringJsonCodec(), List.class, ArrayList::new);
        final var output = new StringWriter();
        codec.write(Arrays.asList("a", null, "b"), new JsonCodec.SerializationContext(new ExtendedWriter(output), type -> {
            throw new AssertionError("Typed collections must not look up a codec");
        }, false));
        assertEquals("[\"a\",null,\"b\"]", output.toString());
    }

    private JsonCodec<Object> namedCodec(final Class<?> type, final String name) {
        return new JsonCodec<>() {
            @Override
            public Type type() {
                return type;
            }

            @Override
            public Object read(final DeserializationContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void write(final Object value, final SerializationContext context) throws IOException {
                context.writer().write('"');
                context.writer().write(name);
                context.writer().write('"');
            }
        };
    }

    private static class Parent {
    }

    private static final class Child extends Parent {
    }
}
