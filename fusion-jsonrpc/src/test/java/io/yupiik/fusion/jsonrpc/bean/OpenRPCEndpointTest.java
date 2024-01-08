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
package io.yupiik.fusion.jsonrpc.bean;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.http.server.api.WebServer;
import io.yupiik.fusion.http.server.impl.tomcat.TomcatWebServerConfiguration;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.formatter.SimplePrettyFormatter;
import io.yupiik.fusion.json.internal.framework.JsonMapperBean;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenRPCEndpointTest {
    @Test
    void render() throws ExecutionException, InterruptedException {
        try (final var container = ConfiguringContainer.of()
                .disableAutoDiscovery(true)
                .register(new JsonMapperBean(), new OpenRPCEndpoint(), new FusionBean<WebServer.Configuration>() {
                    @Override
                    public Type type() {
                        return WebServer.Configuration.class;
                    }

                    @Override
                    public WebServer.Configuration create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                        return new TomcatWebServerConfiguration().host("somehost").port(1234);
                    }
                })
                .start();
             final var mapperInstance = container.lookup(JsonMapper.class);
             final var endpoint = container.lookup(OpenRPCEndpoint.Impl.class)) {
            final var openrpc = mapperInstance.instance().toString(endpoint.instance()
                    .invoke(new JsonRpcMethod.Context(null, null))
                    .toCompletableFuture().get());
            assertEquals("""
                            {
                              "components": {
                                "schemas": {
                                  "test.p.JsonRpcEndpoints.MyInput": {
                                    "type": "object",
                                    "properties": {
                                      "name": {
                                        "nullable": true,
                                        "type": "string"
                                      }
                                    },
                                    "$id": "test.p.JsonRpcEndpoints.MyInput"
                                  },
                                  "test.p.JsonRpcEndpoints.MyResult": {
                                    "type": "object",
                                    "properties": {
                                      "name": {
                                        "nullable": true,
                                        "type": "string"
                                      }
                                    },
                                    "$id": "test.p.JsonRpcEndpoints.MyResult"
                                  }
                                }
                              },
                              "info": {
                                "version": "1.0.0",
                                "title": "JSON-RPC API"
                              },
                              "methods": [
                                {
                                  "description": "",
                                  "errors": [
                                    {
                                      "code": -32700,
                                      "message": "Request deserialization error."
                                    },
                                    {
                                      "code": -32603,
                                      "message": "Exception message, missing JSON-RPC response."
                                    },
                                    {
                                      "code": -32601,
                                      "message": "Unknown JSON-RPC method."
                                    },
                                    {
                                      "code": -32600,
                                      "message": "Invalid request: wrong JSON-RPC version attribute or request JSON type."
                                    },
                                    {
                                      "code": -2,
                                      "message": "Exception message, unhandled exception"
                                    }
                                  ],
                                  "name": "arg",
                                  "params": [
                                    {
                                      "name": "wrapper",
                                      "schema": {
                                        "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyInput"
                                      }
                                    }
                                  ],
                                  "paramStructure": "either",
                                  "result": {
                                    "name": "result",
                                    "schema": {
                                      "nullable": true,
                                      "additionalProperties": {
                                        "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyResult"
                                      },
                                      "type": "object"
                                    }
                                  },
                                  "summary": ""
                                },
                                {
                                  "description": "",
                                  "errors": [
                                    {
                                      "code": -32700,
                                      "message": "Request deserialization error."
                                    },
                                    {
                                      "code": -32603,
                                      "message": "Exception message, missing JSON-RPC response."
                                    },
                                    {
                                      "code": -32601,
                                      "message": "Unknown JSON-RPC method."
                                    },
                                    {
                                      "code": -32600,
                                      "message": "Invalid request: wrong JSON-RPC version attribute or request JSON type."
                                    },
                                    {
                                      "code": -2,
                                      "message": "Exception message, unhandled exception"
                                    }
                                  ],
                                  "name": "fail",
                                  "params": [
                                    {
                                      "name": "direct",
                                      "schema": {
                                        "nullable": false,
                                        "type": "boolean"
                                      }
                                    }
                                  ],
                                  "paramStructure": "either",
                                  "result": {
                                    "name": "result",
                                    "schema": {
                                      "nullable": true,
                                      "additionalProperties": {
                                        "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyResult"
                                      },
                                      "type": "object"
                                    }
                                  },
                                  "summary": ""
                                },
                                {
                                  "description": "",
                                  "errors": [
                                    {
                                      "code": -32700,
                                      "message": "Request deserialization error."
                                    },
                                    {
                                      "code": -32603,
                                      "message": "Exception message, missing JSON-RPC response."
                                    },
                                    {
                                      "code": -32601,
                                      "message": "Unknown JSON-RPC method."
                                    },
                                    {
                                      "code": -32600,
                                      "message": "Invalid request: wrong JSON-RPC version attribute or request JSON type."
                                    },
                                    {
                                      "code": -2,
                                      "message": "Exception message, unhandled exception"
                                    }
                                  ],
                                  "name": "paramTypes",
                                  "params": [
                                    {
                                      "name": "object",
                                      "schema": {
                                        "nullable": true,
                                        "additionalProperties": true,
                                        "type": "object"
                                      }
                                    },
                                    {
                                      "name": "bool",
                                      "schema": {
                                        "nullable": false,
                                        "type": "boolean"
                                      }
                                    },
                                    {
                                      "name": "boolWrapper",
                                      "schema": {
                                        "nullable": true,
                                        "type": "boolean"
                                      }
                                    },
                                    {
                                      "name": "integer",
                                      "schema": {
                                        "nullable": false,
                                        "format": "int32",
                                        "type": "integer"
                                      }
                                    },
                                    {
                                      "name": "intWrapper",
                                      "schema": {
                                        "nullable": true,
                                        "format": "int32",
                                        "type": "integer"
                                      }
                                    },
                                    {
                                      "name": "longNumber",
                                      "schema": {
                                        "nullable": false,
                                        "format": "int64",
                                        "type": "integer"
                                      }
                                    },
                                    {
                                      "name": "longWrapper",
                                      "schema": {
                                        "nullable": true,
                                        "format": "int64",
                                        "type": "integer"
                                      }
                                    },
                                    {
                                      "name": "string",
                                      "schema": {
                                        "nullable": true,
                                        "type": "string"
                                      }
                                    },
                                    {
                                      "name": "model",
                                      "schema": {
                                        "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyInput"
                                      }
                                    },
                                    {
                                      "name": "objectList",
                                      "schema": {
                                        "nullable": true,
                                        "type": "array",
                                        "items": {
                                          "nullable": true,
                                          "additionalProperties": true,
                                          "type": "object"
                                        }
                                      }
                                    },
                                    {
                                      "name": "boolWrapperList",
                                      "schema": {
                                        "nullable": true,
                                        "type": "array",
                                        "items": {
                                          "nullable": true,
                                          "type": "boolean"
                                        }
                                      }
                                    },
                                    {
                                      "name": "intWrapperList",
                                      "schema": {
                                        "nullable": true,
                                        "type": "array",
                                        "items": {
                                          "nullable": true,
                                          "format": "int32",
                                          "type": "integer"
                                        }
                                      }
                                    },
                                    {
                                      "name": "longWrapperList",
                                      "schema": {
                                        "nullable": true,
                                        "type": "array",
                                        "items": {
                                          "nullable": true,
                                          "format": "int64",
                                          "type": "integer"
                                        }
                                      }
                                    },
                                    {
                                      "name": "stringList",
                                      "schema": {
                                        "nullable": true,
                                        "type": "array",
                                        "items": {
                                          "nullable": true,
                                          "type": "string"
                                        }
                                      }
                                    },
                                    {
                                      "name": "modelList",
                                      "schema": {
                                        "nullable": true,
                                        "type": "array",
                                        "items": {
                                          "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyInput"
                                        }
                                      }
                                    },
                                    {
                                      "name": "objectMap",
                                      "schema": {
                                        "nullable": true,
                                        "additionalProperties": {
                                          "nullable": true,
                                          "additionalProperties": true,
                                          "type": "object"
                                        },
                                        "type": "object"
                                      }
                                    },
                                    {
                                      "name": "boolWrapperMap",
                                      "schema": {
                                        "nullable": true,
                                        "additionalProperties": {
                                          "nullable": true,
                                          "type": "boolean"
                                        },
                                        "type": "object"
                                      }
                                    },
                                    {
                                      "name": "intWrapperMap",
                                      "schema": {
                                        "nullable": true,
                                        "additionalProperties": {
                                          "nullable": true,
                                          "format": "int32",
                                          "type": "integer"
                                        },
                                        "type": "object"
                                      }
                                    },
                                    {
                                      "name": "longWrapperMap",
                                      "schema": {
                                        "nullable": true,
                                        "additionalProperties": {
                                          "nullable": true,
                                          "format": "int64",
                                          "type": "integer"
                                        },
                                        "type": "object"
                                      }
                                    },
                                    {
                                      "name": "stringMap",
                                      "schema": {
                                        "nullable": true,
                                        "additionalProperties": {
                                          "nullable": true,
                                          "type": "string"
                                        },
                                        "type": "object"
                                      }
                                    },
                                    {
                                      "name": "modelMap",
                                      "schema": {
                                        "nullable": true,
                                        "additionalProperties": {
                                          "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyInput"
                                        },
                                        "type": "object"
                                      }
                                    }
                                  ],
                                  "paramStructure": "either",
                                  "result": {
                                    "name": "result",
                                    "schema": {
                                      "nullable": true,
                                      "type": "string"
                                    }
                                  },
                                  "summary": ""
                                },
                                {
                                  "description": "",
                                  "errors": [
                                    {
                                      "code": -32700,
                                      "message": "Request deserialization error."
                                    },
                                    {
                                      "code": -32603,
                                      "message": "Exception message, missing JSON-RPC response."
                                    },
                                    {
                                      "code": -32601,
                                      "message": "Unknown JSON-RPC method."
                                    },
                                    {
                                      "code": -32600,
                                      "message": "Invalid request: wrong JSON-RPC version attribute or request JSON type."
                                    },
                                    {
                                      "code": -2,
                                      "message": "Exception message, unhandled exception"
                                    }
                                  ],
                                  "name": "req",
                                  "params": [
                                    {
                                      "name": "input",
                                      "schema": {
                                        "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyInput"
                                      }
                                    }
                                  ],
                                  "paramStructure": "either",
                                  "result": {
                                    "name": "result",
                                    "schema": {
                                      "nullable": true,
                                      "additionalProperties": {
                                        "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyResult"
                                      },
                                      "type": "object"
                                    }
                                  },
                                  "summary": ""
                                },
                                {
                                  "description": "",
                                  "errors": [
                                    {
                                      "code": -32700,
                                      "message": "Request deserialization error."
                                    },
                                    {
                                      "code": -32603,
                                      "message": "Exception message, missing JSON-RPC response."
                                    },
                                    {
                                      "code": -32601,
                                      "message": "Unknown JSON-RPC method."
                                    },
                                    {
                                      "code": -32600,
                                      "message": "Invalid request: wrong JSON-RPC version attribute or request JSON type."
                                    },
                                    {
                                      "code": -2,
                                      "message": "Exception message, unhandled exception"
                                    }
                                  ],
                                  "name": "test1",
                                  "params": [],
                                  "paramStructure": "either",
                                  "result": {
                                    "name": "result",
                                    "schema": {
                                      "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyResult"
                                    }
                                  },
                                  "summary": ""
                                },
                                {
                                  "description": "",
                                  "errors": [
                                    {
                                      "code": -32700,
                                      "message": "Request deserialization error."
                                    },
                                    {
                                      "code": -32603,
                                      "message": "Exception message, missing JSON-RPC response."
                                    },
                                    {
                                      "code": -32601,
                                      "message": "Unknown JSON-RPC method."
                                    },
                                    {
                                      "code": -32600,
                                      "message": "Invalid request: wrong JSON-RPC version attribute or request JSON type."
                                    },
                                    {
                                      "code": -2,
                                      "message": "Exception message, unhandled exception"
                                    }
                                  ],
                                  "name": "test2",
                                  "params": [],
                                  "paramStructure": "either",
                                  "result": {
                                    "name": "result",
                                    "schema": {
                                      "nullable": true,
                                      "additionalProperties": {
                                        "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyResult"
                                      },
                                      "type": "object"
                                    }
                                  },
                                  "summary": ""
                                }
                              ],
                              "openrpc": "1.2.6",
                              "servers": [
                                {
                                  "url": "http://somehost:1234/jsonrpc"
                                }
                              ]
                            }""",
                    new SimplePrettyFormatter(mapperInstance.instance()).apply(openrpc));
        }
    }
}
