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
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnknownFieldsTest {
    @Test
    void mapperConfigurationAndChildrenAreIndependent() {
        for (final var reject : List.of(false, true)) {
            try (final var parent = new JsonMapperImpl(List.of(new LegacyMetadataCodec()),
                    key -> "fusion.json.rejectUnknownFields".equals(key) ? Optional.of(Boolean.toString(reject)) : Optional.empty());
                 final var inherited = parent.as(JsonMapper.Configuring.class).orElseThrow().build();
                 final var strict = parent.as(JsonMapper.Configuring.class).orElseThrow().rejectUnknownFields(true).build();
                 final var loose = strict.as(JsonMapper.Configuring.class).orElseThrow().rejectUnknownFields(false).serializeNulls().build()) {
                final var json = "{\"unknown\":null,\"name\":\"after\"}";
                for (final var mode : List.of(parent, inherited)) {
                    if (reject) {
                        assertThrows(JsonException.class, () -> mode.fromString(String.class, json));
                    } else {
                        assertEquals("after", mode.fromString(String.class, json));
                    }
                }
                assertThrows(JsonException.class, () -> strict.fromString(String.class, json));
                assertEquals("after", loose.fromString(String.class, json));
                assertEquals("known", strict.fromString(String.class, "{\"name\":\"known\"}"));
            }
        }
    }

    @Test
    void existingContextConstructorsDefaultToAcceptingUnknownFields() {
        try (final var parser = parser("{}")) {
            assertFalse(new JsonCodec.DeserializationContext(parser, type -> null).rejectUnknownFields());
            assertFalse(new JsonCodec.DeserializationContext(parser, type -> null, true).rejectUnknownFields());
            assertFalse(new JsonCodec.DeserializationContext(parser, type -> null, false).rejectUnknownFields());
            final var context = new JsonCodec.DeserializationContext(parser, type -> null, false, true);
            assertTrue(context.rejectUnknownFields());
            assertFalse(context.rejectIncompatibleValues());
        }
    }

    @Test
    void existingReadObjectSignatureHonorsTheNewFlag() throws IOException {
        final var codec = new LegacyMetadataCodec();
        for (final var value : List.of("null", "true", "42", "\"text\"", "{\"nested\":[{}]}", "[{},null]")) {
            final var json = "{\"unknown\":" + value + ",\"name\":\"after\"}";
            try (final var parser = parser(json)) {
                assertEquals("after", codec.read(new JsonCodec.DeserializationContext(parser, type -> null)));
            }
            try (final var parser = parser(json)) {
                final var context = new JsonCodec.DeserializationContext(parser, type -> null, true, true);
                final var error = assertThrows(JsonException.class, () -> codec.read(context));
                assertTrue(error.getMessage().contains("unknown"), error.getMessage());
            }
        }
        try (final var parser = parser("{\"name\":\"known\"}")) {
            assertEquals("known", codec.read(new JsonCodec.DeserializationContext(parser, type -> null, true, true)));
        }
    }

    private JsonParser parser(final String json) {
        return new JsonParser(new StringReader(json), 16, new BufferProvider(64, -1), true);
    }

    // Same FieldMeta constructor and readObject call emitted before ignored-name metadata was added.
    private static class LegacyMetadataCodec extends BaseJsonCodec<String> {
        private static final char[][] KEYS = {"name".toCharArray()};
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static final FieldMeta<String>[] FIELDS = new FieldMeta[]{
                new FieldMeta<String>(KEYS[0], 0, ContainerKind.VALUE, ValueKind.STRING,
                        true, false, null, value -> value, 0, "\"name\":".toCharArray())
        };

        private LegacyMetadataCodec() {
            super(String.class);
        }

        @Override
        public String read(final DeserializationContext context) throws IOException {
            return readObject(context, KEYS, length -> length == 4 ? 0 : -1, FIELDS, slots -> (String) slots[0]);
        }

        @Override
        public void write(final String value, final SerializationContext context) {
            throw new UnsupportedOperationException();
        }
    }
}
