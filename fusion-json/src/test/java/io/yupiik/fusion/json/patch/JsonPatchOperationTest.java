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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.yupiik.fusion.json.patch.JsonPatchOperation.Operation.add;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonPatchOperationTest {
    @Test
    void deserialization() {
        try (final var mapper = new JsonMapperImpl(List.of(), c -> Optional.empty())) {
            assertEquals(new JsonPatchOperation(add, "/test", null, BigDecimal.ONE), mapper.fromString(JsonPatchOperation.class, """
                    {"op":"add","path":"/test","value":1}"""));
            assertEquals(new JsonPatchOperation(add, "/test", null, "v-1"), mapper.fromString(JsonPatchOperation.class, """
                    {"op":"add","path":"/test","value":"v-1"}"""));
            assertEquals(new JsonPatchOperation(add, "/test", null, Map.of("foo", "bar")), mapper.fromString(JsonPatchOperation.class, """
                    {"op":"add","path":"/test","value":{"foo":"bar"}}"""));
            assertEquals(new JsonPatchOperation(add, "/test", null, List.of("foo", "bar")), mapper.fromString(JsonPatchOperation.class, """
                    {"op":"add","path":"/test","value":["foo","bar"]}"""));
            assertEquals(new JsonPatchOperation(add, "/test", null, true), mapper.fromString(JsonPatchOperation.class, """
                    {"op":"add","path":"/test","value":true}"""));
            assertEquals(new JsonPatchOperation(add, "/test", null, false), mapper.fromString(JsonPatchOperation.class, """
                    {"op":"add","path":"/test","value":false}"""));
        }
    }
}
