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
package io.yupiik.fusion.json;

import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.internal.parser.ValidatingParser;
import io.yupiik.fusion.json.spi.Parser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDepthTest {
    @Test
    void boundariesCountOnlyOpenContainers() {
        try (final var parent = new JsonMapperImpl(List.of(), key -> Optional.empty());
             final var limited = parent.as(JsonMapper.Configuring.class).orElseThrow().maxDepth(2).build()) {
            for (final var json : List.of("null", "42", "true", "\"text\"", "{}", "[]",
                    "[{},[],42]", "{\"a\":{},\"b\":[true]}")) {
                assertEquals(parent.fromString(Object.class, json), limited.fromString(Object.class, json), json);
            }
            for (final var json : List.of("[[[]]]", "{\"a\":{\"b\":{}}}", "[{\"a\":[]}]")) {
                final var error = assertThrows(JsonException.class, () -> limited.fromString(Object.class, json), json);
                assertTrue(error.getMessage().contains("maxDepth=2"), error.getMessage());
                parent.fromString(Object.class, json);
            }
            final var list = new Types.ParameterizedTypeImpl(List.class, Object.class);
            assertEquals(List.of(Map.of()), limited.fromString(list, "[{}]"));
            assertThrows(JsonException.class, () -> limited.fromString(list, "[[{}]]"));
        }
    }

    @Test
    void settingsAreValidatedAndChildrenRemainIndependent() {
        assertThrows(IllegalArgumentException.class,
                () -> new JsonMapperImpl(List.of(), key -> Optional.of("-1")));
        try (final var parent = new JsonMapperImpl(List.of(),
                key -> "fusion.json.maxDepth".equals(key) ? Optional.of("1") : Optional.empty());
             final var inherited = parent.as(JsonMapper.Configuring.class).orElseThrow().build();
             final var unlimited = parent.as(JsonMapper.Configuring.class).orElseThrow().maxDepth(0).build();
             final var compatible = parent.as(JsonMapper.Configuring.class).orElseThrow()
                     .rejectIncompatibleValues(false).rejectTrailingTokens(false).rejectDuplicateKeys(true).build()) {
            assertThrows(IllegalArgumentException.class,
                    () -> parent.as(JsonMapper.Configuring.class).orElseThrow().maxDepth(-1));
            for (final var mapper : List.of(parent, inherited, compatible)) {
                assertThrows(JsonException.class, () -> mapper.fromString(Object.class, "[[]]"));
                assertEquals(List.of(), mapper.fromString(Object.class, "[]"));
            }
            assertEquals(List.of(List.of()), unlimited.fromString(Object.class, "[[]]"));
            assertEquals(List.of(), compatible.fromString(Object.class, "[] ignored"));
        }
    }

    @Test
    void customFactoriesAreWrappedForBothReadOverloads() {
        try (final var mapper = new JsonMapperImpl(List.of(), key -> Optional.empty(),
                reader -> new JsonParser(reader, 128, new BufferProvider(128, -1), true));
             final var limited = mapper.as(JsonMapper.Configuring.class).orElseThrow().maxDepth(1).build()) {
            assertThrows(JsonException.class, () -> limited.read(Object.class, new StringReader("[{}]")));
            assertThrows(JsonException.class, () -> limited.read(
                    new Types.ParameterizedTypeImpl(Map.class, String.class, Object.class),
                    new StringReader("{\"a\":[]}")));
        }
    }

    @Test
    void replayDoesNotCountTwiceAndSkipsEnforceTheLimit() {
        for (final var duplicateCheck : List.of(false, true)) {
            try (final var parser = parser("[{\"x\":[]}]", duplicateCheck, 3)) {
                for (final var expected : List.of(Parser.Event.START_ARRAY, Parser.Event.START_OBJECT,
                        Parser.Event.KEY_NAME, Parser.Event.START_ARRAY)) {
                    parser.enforceNext(expected);
                    parser.rewind(expected);
                    parser.enforceNext(expected);
                }
                parser.enforceNext(Parser.Event.END_ARRAY);
                parser.enforceNext(Parser.Event.END_OBJECT);
                parser.enforceNext(Parser.Event.END_ARRAY);
                parser.ensureDocument(true);
            }
            try (final var parser = parser("{\"x\":[{}]}", duplicateCheck, 2)) {
                parser.enforceNext(Parser.Event.START_OBJECT);
                assertThrows(JsonException.class, parser::skipObject);
            }
            try (final var parser = parser("[[{}]]", duplicateCheck, 2)) {
                parser.enforceNext(Parser.Event.START_ARRAY);
                assertThrows(JsonException.class, parser::skipArray);
            }
        }
    }

    private ValidatingParser parser(final String json, final boolean duplicates, final int maxDepth) {
        return new ValidatingParser(new JsonParser(new StringReader(json), 128,
                new BufferProvider(128, -1), true), duplicates, maxDepth);
    }
}
