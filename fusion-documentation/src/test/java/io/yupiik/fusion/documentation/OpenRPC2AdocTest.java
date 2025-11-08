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
package io.yupiik.fusion.documentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenRPC2AdocTest {
    @Test
    void render(@TempDir final Path work) throws IOException {
        final var spec = Files.writeString(work.resolve("openrpc.json"), """
                {
                  "schemas": {
                    "org.example.application.jsonrpc.Greeting": {
                      "title": "Greeting",
                      "type": "object",
                      "properties": {
                        "message": {
                          "nullable": true,
                          "type": "string"
                        }
                      },
                      "$id": "org.example.application.jsonrpc.Greeting"
                    }
                  },
                  "methods": {
                    "greet": {
                      "description": "Returns some greeting.",
                      "errors": [
                        {
                          "code": 400,
                          "message": "Invalid incoming data."
                        }
                      ],
                      "name": "greet",
                      "paramStructure": "either",
                      "params": [
                        {
                          "name": "name",
                          "schema": {
                            "nullable": true,
                            "type": "string"
                          }
                        }
                      ],
                      "result": {
                        "name": "result",
                        "schema": {
                          "$ref": "#/schemas/org.example.application.jsonrpc.Greeting"
                        }
                      },
                      "summary": "Returns some greeting."
                    }
                  }
                }""");
        final var out = work.resolve("out.adoc");
        new OpenRPC2Adoc(Map.of("input", spec.toString(), "output", out.toString())).run();
        assertEquals("""
                == Methods
                                
                === greet
                                
                Parameter structure: either.
                                
                Returns some greeting.
                                
                Parameters:
                * `name` (`string`)
                                
                                
                                
                == Schemas
                                
                === Greeting (org.example.application.jsonrpc.Greeting) schema
                                
                [cols="m,1a,m,3a"]
                |===
                |Name |Type |Nullable |Description
                                
                |message
                |`string`
                |true
                |-
                |===
                                
                """, Files.readString(out));
    }
}
