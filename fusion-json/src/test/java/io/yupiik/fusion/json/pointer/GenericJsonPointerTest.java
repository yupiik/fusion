/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.json.pointer;

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericJsonPointerTest {
    @Test
    void testGetValueWithWholeDocument() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("");
        final var result = jsonPointer.apply(jsonDocument);
        assertEquals(jsonDocument.toString(), result.toString());
    }

    @Test
    void testGetValue0() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/");
        final var result = jsonPointer.apply(jsonDocument);
        assertEquals("0", result.toString());
    }

    @Test
    void testGetValue1() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/a~1b");
        final var result = jsonPointer.apply(jsonDocument);
        assertEquals("1", result.toString());
    }

    @Test
    void testGetValue2() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/c%d");
        final var result = jsonPointer.apply(jsonDocument);
        assertEquals("2", result.toString());
    }

    @Test
    void testGetValue3() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/e^f");
        final var result = jsonPointer.apply(jsonDocument);
        assertEquals("3", result.toString());
    }

    @Test
    void testGetValue4() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/g|h");
        final var result = jsonPointer.apply(jsonDocument);
        assertEquals("4", result.toString());
    }

    @Test
    void testGetValue5() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/i\\j");
        final var result = jsonPointer.apply(jsonDocument);
        assertJson("5", result);
    }

    @Test
    void testGetValue6() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/k\"l");
        final var result = jsonPointer.apply(jsonDocument);
        assertJson("6", result);
    }

    @Test
    void testGetValue7() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/ ");
        final var result = jsonPointer.apply(jsonDocument);
        assertJson("7", result);
    }

    @Test
    void testGetValue8() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/m~0n");
        final var result = jsonPointer.apply(jsonDocument);
        assertJson("8", result);
    }

    @Test
    void testGetValueWithWholeJsonArray() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/foo");
        final var result = jsonPointer.apply(jsonDocument);
        assertJson("[\"bar\",\"baz\"]", result);
    }

    @Test
    void testGetValueWithJsonArray() {
        final var jsonDocument = sample();
        final var jsonPointer = new GenericJsonPointer("/foo/0");
        final var result = jsonPointer.apply(jsonDocument);
        assertJson("\"bar\"", result);
    }

    @Test
    void testAddJsonStructureWithEmptyJsonPointer() {
        final var jsonPointer = new GenericJsonPointer("");
        final var target = new HashMap<>();
        final var value = Map.of("foo", "bar");
        final var result = jsonPointer.add(target, value);
        assertJson("{\"foo\":\"bar\"}", result);
    }

    @Test
    void testAddObject() {
        final var jsonPointer = new GenericJsonPointer("/child");
        final var target = Map.of("foo", "bar");
        final var value = Map.of("grandchild", Map.of());
        final var result = jsonPointer.add(target, value);
        assertJson("{\"foo\":\"bar\",\"child\":{\"grandchild\":{}}}", result);
    }

    @Test
    void testAddObjectMember() {
        final var jsonPointer = new GenericJsonPointer("/baz");
        final var target = Map.of("foo", "bar");
        final var result = jsonPointer.add(target, "qux");
        assertJson("{\"foo\":\"bar\",\"baz\":\"qux\"}", result);
    }

    @Test
    void testAddFirstObjectMember() {
        final var jsonPointer = new GenericJsonPointer("/foo");
        final var target = new HashMap<>();
        final var result = jsonPointer.add(target, "bar");
        assertJson("{\"foo\":\"bar\"}", result);
    }

    @Test
    void testAddReplaceObjectMember() {
        final var jsonPointer = new GenericJsonPointer("/baz");
        final var target = new LinkedHashMap<String, Object>();
        target.put("baz", "qux");
        target.put("foo", "bar");
        final var result = jsonPointer.add(target, "boo");
        assertJson("{\"baz\":\"boo\",\"foo\":\"bar\"}", result);
    }

    @Test
    void testAddArrayElement() {
        final var jsonPointer = new GenericJsonPointer("/foo/1");
        final var target = Map.of("foo", List.of("bar", "baz"));
        final var result = jsonPointer.add(target, "qux");
        assertJson("{\"foo\":[\"bar\",\"qux\",\"baz\"]}", result);
    }

    @Test
    void testAddLastArrayElementSimple() {
        final var jsonPointer = new GenericJsonPointer("/-");
        final var target = List.of("bar", "qux", "baz");
        final var result = jsonPointer.add(target, "xyz");
        assertJson("[\"bar\",\"qux\",\"baz\",\"xyz\"]", result);
    }

    @Test
    void testRemoveObjectMember() {
        final var jsonPointer = new GenericJsonPointer("/baz");
        final var target = Map.of("baz", "qux", "foo", "bar");
        final var result = jsonPointer.remove(target);
        assertJson("{\"foo\":\"bar\"}", result);
    }

    @Test
    void testRemoveFieldMemberWithObjectAndArray() {
        final var jsonPointer = new GenericJsonPointer("/test/status");
        final var target = Map.of("test", Map.of("status", "200"), "array", List.of());
        final var result = jsonPointer.remove(target);
        assertJson("{\"test\":{},\"array\":[]}", result);
    }

    private Object sample() {
        return Stream.of(
                        entry("foo", List.of("bar", "baz")),
                        entry("", 0),
                        entry("a/b", 1),
                        entry("c%d", 2),
                        entry("e^f", 3),
                        entry("g|h", 4),
                        entry("i\\j", 5),
                        entry("k\"l", 6),
                        entry(" ", 7),
                        entry("m~n", 8))
                .collect(Collector.of(
                        LinkedHashMap::new, // stay sorted, Map.of() does not sadly
                        (a, b) -> a.put(b.getKey(), b.getValue()),
                        (a, b) -> {
                            a.putAll(b);
                            return a;
                        }));
    }

    private void assertJson(final String json, final Object value) {
        try (final var mapper = new JsonMapperImpl(List.of(), c -> empty())) {
            final var expected = mapper.fromString(Object.class, json);
            assertEquals(expected instanceof BigDecimal b ? b.intValue() : expected, value);
        }
    }
}
