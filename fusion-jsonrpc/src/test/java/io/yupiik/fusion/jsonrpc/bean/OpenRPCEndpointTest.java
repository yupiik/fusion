package io.yupiik.fusion.jsonrpc.bean;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.http.server.api.WebServer;
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
                        return new WebServer.Configuration() {
                            @Override
                            public WebServer.Configuration fusionServletMapping(final String mapping) {
                                return null;
                            }

                            @Override
                            public WebServer.Configuration utf8Setup(final boolean enabled) {
                                return null;
                            }

                            @Override
                            public WebServer.Configuration base(final String webappBaseDir) {
                                return null;
                            }

                            @Override
                            public WebServer.Configuration port(final int port) {
                                return null;
                            }

                            @Override
                            public WebServer.Configuration host(final String host) {
                                return null;
                            }

                            @Override
                            public WebServer.Configuration accessLogPattern(final String accessLogPattern) {
                                return null;
                            }

                            @Override
                            public String host() {
                                return "somehost";
                            }

                            @Override
                            public int port() {
                                return 1234;
                            }
                        };
                    }
                })
                .start();
             final var mapperInstance = container.lookup(JsonMapper.class);
             final var endpoint = container.lookup(OpenRPCEndpoint.Impl.class)) {
            final var openrpc = endpoint.instance()
                    .invoke(new JsonRpcMethod.Context(null, null))
                    .toCompletableFuture().get()
                    .toString();
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
                                  "errors": [],
                                  "name": "arg",
                                  "paramStructure": "either",
                                  "params": [
                                    {
                                      "name": "wrapper",
                                      "schema": {
                                        "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyInput"
                                      }
                                    }
                                  ],
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
                                  "errors": [],
                                  "name": "fail",
                                  "paramStructure": "either",
                                  "params": [
                                    {
                                      "name": "direct",
                                      "schema": {
                                        "nullable": false,
                                        "type": "boolean"
                                      }
                                    }
                                  ],
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
                                  "errors": [],
                                  "name": "paramTypes",
                                  "paramStructure": "either",
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
                                  "errors": [],
                                  "name": "req",
                                  "paramStructure": "either",
                                  "params": [
                                    {
                                      "name": "input",
                                      "schema": {
                                        "$ref": "#/components/schemas/test.p.JsonRpcEndpoints.MyInput"
                                      }
                                    }
                                  ],
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
                                  "errors": [],
                                  "name": "test1",
                                  "paramStructure": "either",
                                  "params": [],
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
                                  "errors": [],
                                  "name": "test2",
                                  "paramStructure": "either",
                                  "params": [],
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
