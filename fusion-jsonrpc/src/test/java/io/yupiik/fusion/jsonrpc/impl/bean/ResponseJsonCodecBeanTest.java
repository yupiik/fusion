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
package io.yupiik.fusion.jsonrpc.impl.bean;

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.pretty.PrettyJsonMapper;
import io.yupiik.fusion.jsonrpc.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseJsonCodecBeanTest {
    @Test
    void listResult() {
        final var json = """
                {
                  "jsonrpc": "2.0",
                  "result": [
                    {
                      "code": 1235,
                      "message": "oops1",
                      "data": {
                        "msg1": "test error 1"
                      }
                    }
                  ],
                  "error": {
                    "code": 1234,
                    "message": "oops",
                    "data": {
                      "msg": "test error"
                    }
                  }
                }""";
        final var object = new Response(
                "2.0", null,
                List.of(new Response.ErrorResponse(1235, "oops1", Map.of("msg1", "test error 1"))),
                new Response.ErrorResponse(1234, "oops", Map.of("msg", "test error")));

        try (final var mapper = new PrettyJsonMapper(new JsonMapperImpl(
                List.of(
                        new ResponseJsonCodecBean().create(null, null),
                        new ErrorResponseJsonCodecBean().create(null, null)),
                key -> Optional.empty()))) {
            assertEquals(json, mapper.toString(object));
        }
    }
}
