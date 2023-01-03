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
package io.yupiik.fusion.httpclient.core.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NDJSONHttpClientTest {
    @Test
    void json(@TempDir final Path temp) throws Exception {
        final var ndJson = Files.writeString(temp.resolve("mock.ndjson"), "" +
                "{" +
                "  \"request\": {" +
                "    \"method\": \"POST\"," +
                "    \"url\": \"http://localhost:123456/post\"," + // wrong port to avoid to actually call it
                "    \"httpVersion\": \"HTTP/1.1\"," +
                "    \"cookies\": []," +
                "    \"headers\": [" +
                "      {" +
                "        \"name\": \"content-type\"," +
                "        \"value\": \"application/json\"" +
                "      }" +
                "    ]," +
                "    \"queryString\": []," +
                "    \"postData\": {" +
                "      \"text\": \"{\\n  \\\"foo\\\": \\\"bar\\\"\\n}\"," +
                "      \"mimeType\": \"application/json\"" +
                "    }," +
                "    \"bodySize\": -1," +
                "    \"headersSize\": -1" +
                "  }," +
                "  \"response\": {" +
                "    \"status\": 200," +
                "    \"statusText\": \"OK\"," +
                "    \"httpVersion\": \"HTTP/1.1\"," +
                "    \"headers\": [" +
                "      {\"name\": \"Accept\",\"value\": \"*/*\" }," +
                "      {\"name\": \"Accept-Encoding\",\"value\": \"gzip,deflate\" }," +
                "      {\"name\": \"Content-Length\",\"value\": \"19\" }," +
                "      {\"name\": \"Content-Type\",\"value\": \"application/json\" }," +
                "      {\"name\": \"Host\",\"value\": \"httpbin.org\" }," +
                "      {\"name\": \"User-Agent\",\"value\": \"node-fetch/1.0 (+https://github.com/bitinn/node-fetch)\" }" +
                "    ]," +
                "    \"content\": {" +
                "      \"size\": 19," +
                "      \"mimeType\": \"application/json\"," +
                "      \"text\": \"{\\\"the-result\\\":\\\"yes\\\"}\"" +
                "    }" +
                "  }" +
                "}\n");
        try (final var client = new NDJSONHttpClient(new NDJSONHttpClient.Configuration(ndJson))) {
            final var response = client.send(
                    HttpRequest.newBuilder()
                            .POST(HttpRequest.BodyPublishers.ofString("{\"foo\":\"bar\"}"))
                            .uri(URI.create("http://localhost:123456/post"))
                            .header("content-type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("{\"the-result\":\"yes\"}", response.body());
            assertEquals("node-fetch/1.0 (+https://github.com/bitinn/node-fetch)", response.headers().firstValue("User-Agent").orElseThrow());
        }
    }
}
