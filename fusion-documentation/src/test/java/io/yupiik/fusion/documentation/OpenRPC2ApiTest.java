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

class OpenRPC2ApiTest {
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
        new OpenRPC2OpenAPI(Map.of(
                "input", spec.toString(),
                "output", out.toString(),
                "info.title", "The API",
                "info.version", "1.2.3",
                "info.description", "A super API.",
                "servers.main.url", "https://api.company.com/jsonrpc",
                "servers.main.description", "The main server"
        )).run();
        assertEquals("""
                {
                  "components": {
                    "schemas": {
                      "org.example.application.jsonrpc.Greeting": {
                        "properties": {
                          "message": {
                            "nullable": true,
                            "type": "string"
                          }
                        },
                        "title": "Greeting",
                        "type": "object"
                      }
                    }
                  },
                  "info": {
                    "description": "A super API.",
                    "title": "The API",
                    "version": "1.2.3"
                  },
                  "openapi": "3.0.3",
                  "paths": {
                    "/greet": {
                      "post": {
                        "operationId": "greet",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "properties": {
                                  "jsonrpc": {
                                    "default": "2.0",
                                    "description": "JSON-RPC version, should always be '2.0'.",
                                    "type": "string"
                                  },
                                  "method": {
                                    "default": "greet",
                                    "description": "The JSON-RPC method name, should always be 'greet'",
                                    "type": "string"
                                  },
                                  "params": {
                                    "properties": {
                                      "name": {
                                        "nullable": true,
                                        "type": "string"
                                      }
                                    },
                                    "type": "object"
                                  }
                                },
                                "required": [
                                  "jsonrpc",
                                  "method"
                                ],
                                "type": "object"
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "jsonrpc": {
                                      "default": "2.0",
                                      "description": "JSON-RPC version, should always be '2.0'.",
                                      "type": "string"
                                    },
                                    "result": {
                                      "$ref": "#/components/schemas/org.example.application.jsonrpc.Greeting"
                                    }
                                  },
                                  "required": [
                                    "jsonrpc",
                                    "result"
                                  ],
                                  "type": "object"
                                }
                              }
                            },
                            "description": "OK"
                          },
                          "x-jsonrpc-code=400": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "error": {
                                      "properties": {
                                        "code": {
                                          "default": 400,
                                          "description": "A Number that indicates the error type that occurred. This MUST be an integer.",
                                          "type": "integer"
                                        },
                                        "data": {
                                          "type": "object"
                                        },
                                        "message": {
                                          "default": "Invalid incoming data.",
                                          "description": "A String providing a short description of the error. The message SHOULD be limited to a concise single sentence.",
                                          "type": "string"
                                        }
                                      },
                                      "required": [
                                        "code",
                                        "message"
                                      ],
                                      "type": "object"
                                    },
                                    "jsonrpc": {
                                      "default": "2.0",
                                      "description": "JSON-RPC version, should always be '2.0'.",
                                      "type": "string"
                                    }
                                  },
                                  "required": [
                                    "jsonrpc",
                                    "error"
                                  ],
                                  "type": "object"
                                }
                              }
                            },
                            "description": "Invalid incoming data. (Error code=400)"
                          }
                        },
                        "summary": "Returns some greeting."
                      }
                    }
                  },
                  "servers": [
                    {
                      "description": "The main server",
                      "url": "https://api.company.com/jsonrpc"
                    }
                  ]
                }""", Files.readString(out));
    }
}
