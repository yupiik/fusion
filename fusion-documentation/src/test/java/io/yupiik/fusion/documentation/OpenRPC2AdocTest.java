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
                                
                [cols="1,1"]
                |===
                |Name|Type|Nullable
                                
                |message
                |`string`
                |===
                                
                """, Files.readString(out));
    }
}
