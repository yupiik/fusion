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
package io.yupiik.fusion.json.patch;

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.yupiik.fusion.json.patch.JsonPatchOperation.Operation.add;
import static io.yupiik.fusion.json.patch.JsonPatchOperation.Operation.remove;
import static java.util.Optional.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GenericJsonPatchTest {
    @Test
    void testAddObjectMember() {
        final var object = Map.of("foo", "bar");
        final var patched = new GenericJsonPatch(List.of(new JsonPatchOperation(add, "/baz", null, "qux"))).apply(object);
        assertJson("{\"foo\":\"bar\",\"baz\":\"qux\"}", patched);
    }

    @Test
    void testAddToRootContainingEmptyJsonObject() {
        final var object = Map.of("request", Map.of("test", Map.of()));
        final var patched = new GenericJsonPatch(List.of(new JsonPatchOperation(add, "/name", null, "aName"))).apply(object);
        assertJson("{\"request\":{\"test\":{}},\"name\":\"aName\"}", patched);
    }

    @Test
    void testAddArrayElementWithIndex() {
        final var object = Map.of("foo", List.of("bar", "baz"));
        final var patched = new GenericJsonPatch(List.of(new JsonPatchOperation(add, "/foo/1", null, "qux"))).apply(object);
        assertJson("{\"foo\":[\"bar\",\"qux\",\"baz\"]}", patched);
    }

    @Test
    void testRemoveObjectMember() {
        final var object = Map.of("baz", "qux", "foo", "bar");
        final var patched = new GenericJsonPatch(List.of(new JsonPatchOperation(remove, "/baz", null, null))).apply(object);
        assertJson("{\"foo\":\"bar\"}", patched);
    }

    @Test
    void testRemoveArrayElement() {
        final var object = Map.of("foo", List.of("bar", "qux", "baz"));
        final var patched = new GenericJsonPatch(List.of(new JsonPatchOperation(remove, "/foo/1", null, null))).apply(object);
        assertJson("{\"foo\":[\"bar\",\"baz\"]}", patched);
    }

    private void assertJson(final String json, final Object value) {
        try (final var mapper = new JsonMapperImpl(List.of(), c -> empty())) {
            assertEquals(mapper.fromString(Object.class, json), value);
        }
    }
}
