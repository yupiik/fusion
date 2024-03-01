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
package io.yupiik.fusion.json.diff;

import io.yupiik.fusion.json.patch.GenericJsonPatch;
import io.yupiik.fusion.json.patch.JsonPatchOperation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.yupiik.fusion.json.patch.JsonPatchOperation.Operation.add;
import static io.yupiik.fusion.json.patch.JsonPatchOperation.Operation.remove;
import static io.yupiik.fusion.json.patch.JsonPatchOperation.Operation.replace;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class GenericJsonDiffTest {
    @Test
    public void nullDiff() {
        final var list = new ArrayList<>();
        list.add(null);
        final var patch = List.of(new JsonPatchOperation(add, "/testEmpty/0", null, null));
        final var target = Map.of("testEmpty", list);
        final var from = Map.of("testEmpty", List.of());
        assertDiff(from, target, patch);
        final var patched = new GenericJsonPatch(patch).apply(from);
        assertEquals(target, patched);
        assertNotSame(patched, target);
    }

    @Test
    public void fromEmptyArray() {
        assertDiff(
                Map.of("testEmpty", List.of()),
                Map.of("testEmpty", List.of("something")),
                List.of(new JsonPatchOperation(add, "/testEmpty/0", null, "something")));
    }

    @Test
    public void toEmptyArray() {
        assertDiff(
                Map.of("testEmpty", List.of("something")),
                Map.of("testEmpty", List.of()),
                List.of(new JsonPatchOperation(remove, "/testEmpty/0", null, null)));
    }

    @Test
    public void testAddDiff() {
        assertDiff(
                Map.of("a", "xa"),
                new TreeMap<>(Map.of("a", "xa", "b", "xb")),
                List.of(new JsonPatchOperation(add, "/b", null, "xb")));
    }

    @Test
    public void testAddDiffNewObject() {
        assertDiff(
                Map.of("a", new TreeMap<>(Map.of("aa", "value", "ab", "another"))),
                Map.of(),
                List.of(new JsonPatchOperation(remove, "/a", null, null)));
    }

    @Test
    public void testAddDiffNewObjectWithEscaping() {
        assertDiff(
                Map.of("a~/", new TreeMap<>(Map.of("esc/aped", "value", "tilde", "another"))),
                Map.of(),
                List.of(new JsonPatchOperation(remove, "/a~0~1", null, null)));
    }

    @Test
    public void testAddDiffInNestedObject() {
        assertDiff(
                Map.of("a", Map.of("aa", "value")),
                Map.of("a", new TreeMap<>(Map.of("aa", "value", "bb", "another value"))),
                List.of(new JsonPatchOperation(add, "/a/bb", null, "another value")));
    }

    @Test
    public void testRemoveDiffObject() {
        assertDiff(
                Map.of("a", "value"),
                Map.of(),
                List.of(new JsonPatchOperation(remove, "/a", null, null)));
    }

    @Test
    public void testRemoveDiffNestedObject() {
        assertDiff(
                new TreeMap<>(Map.of("a", "value", "nested", Map.of("1", 1, "2", 2))),
                Map.of(),
                List.of(
                        new JsonPatchOperation(remove, "/a", null, null),
                        new JsonPatchOperation(remove, "/nested", null, null)));
    }

    @Test
    public void testDiffEqualObjects() {
        assertDiff(Map.of(), Map.of(), List.of());
    }

    @Test
    public void testDiffReplaceObject() {
        assertDiff(
                Map.of("a", "value"),
                Map.of("a", "replaced"),
                List.of(new JsonPatchOperation(replace, "/a", null, "replaced")));
    }

    @Test
    public void testDiffReplaceFromNestedObject() {
        assertDiff(
                Map.of("a", Map.of("aa", "value")),
                Map.of("a", Map.of("aa", "replaced")),
                List.of(new JsonPatchOperation(replace, "/a/aa", null, "replaced")));
    }

    @Test
    public void testAddValueToArray() {
        assertDiff(
                List.of("first"),
                List.of("first", "second"),
                List.of(new JsonPatchOperation(add, "/1", null, "second")));
    }

    @Test
    public void testAddObjectToArray() {
        assertDiff(
                List.of(Map.of("a", "a")),
                List.of(Map.of("a", "a"), Map.of("a", "b")),
                List.of(new JsonPatchOperation(add, "/1", null, Map.of("a", "b"))));
    }

    @Test
    public void testRemoveValueFromArray() {
        assertDiff(
                List.of("a", "b"),
                List.of("b"),
                List.of(
                        new JsonPatchOperation(replace, "/0", null, "b"),
                        new JsonPatchOperation(remove, "/1", null, null)));
    }

    @Test
    public void testRemoveObjectFromArray() {
        assertDiff(
                List.of(List.of("a", "b")),
                List.of(),
                List.of(
                        new JsonPatchOperation(remove, "/0", null, null)));
    }

    @Test
    public void testComplexDiff() {
        assertDiff(
                new TreeMap<>(Map.of(
                        "a", "xa",
                        "b", 2,
                        "c", Map.of("d", "xd"),
                        "e", List.of(BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(3)))),
                new TreeMap<>(Map.of(
                        "a", "xa",
                        "c", new TreeMap<>(Map.of("d", "xd", "d2", "xd2")),
                        "e", List.of(BigDecimal.ONE, BigDecimal.valueOf(3)),
                        "f", "xf")),
                List.of(
                        new JsonPatchOperation(remove, "/b", null, null),
                        new JsonPatchOperation(add, "/c/d2", null, "xd2"),
                        new JsonPatchOperation(replace, "/e/1", null, BigDecimal.valueOf(3)),
                        new JsonPatchOperation(remove, "/e/2", null, null),
                        new JsonPatchOperation(add, "/f", null, "xf")));
    }

    private void assertDiff(final Object from, final Object to, final List<JsonPatchOperation> expected) {
        final var diff = new GenericJsonDiff(from, to).toPatch();
        assertEquals(expected, diff);
        assertEquals(to, new GenericJsonPatch(diff).apply(from));
    }
}
