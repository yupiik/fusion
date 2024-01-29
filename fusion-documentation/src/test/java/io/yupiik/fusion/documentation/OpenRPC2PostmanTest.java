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

class OpenRPC2PostmanTest {
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
        new OpenRPC2Postman(Map.of(
                "input", spec.toString(),
                "output", out.toString(),
                "info.title", "The API",
                "info.version", "1.2.3",
                "info.description", "A super API.",
                "server.url", "https://api.company.com/jsonrpc"
        )).run();
        assertEquals("""
                {
                  "info": {
                    "description": "A super API.",
                    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
                    "title": "The API",
                    "version": "1.2.3"
                  },
                  "items": [
                    {
                      "description": "Returns some greeting.",
                      "name": "greet",
                      "request": {
                        "body": {
                          "mode": "raw",
                          "options": {},
                          "raw": "{\\n  \\"jsonrpc\\": \\"2.0\\",\\n  \\"method\\": \\"greet\\",\\n  \\"params\\": {\\n    \\"name\\": \\"<string>\\"\\n  }\\n}"
                        },
                        "description": "JSON-RPC endpoint.",
                        "header": [
                          {
                            "description": "Accept header must be application/json.",
                            "key": "Accept",
                            "type": "text",
                            "value": "application/json"
                          },
                          {
                            "description": "Content-Type header must be application/json.",
                            "key": "Content-Type",
                            "type": "text",
                            "value": "application/json"
                          }
                        ],
                        "method": "POST",
                        "url": "{{JSON_RPC_ENDPOINT}}"
                      },
                      "responses": [],
                      "variables": []
                    }
                  ],
                  "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
                  "variables": [
                    {
                      "description": "JSON-RPC endpoint.",
                      "key": "JSON_RPC_ENDPOINT",
                      "type": "text",
                      "value": "https://api.company.com/jsonrpc"
                    }
                  ]
                }""", Files.readString(out));
    }
}
