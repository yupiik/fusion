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
package io.yupiik.fusion.httpclient.core.replay;

import io.yupiik.fusion.httpclient.core.listener.impl.BaseHARDumperListener;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HARHttpClientTest {
    @Test
    void json() throws Exception {
        final var entry = new BaseHARDumperListener.Har.Entry(
                null, null, 0L, null,
                new BaseHARDumperListener.Har.Response(
                        123, null, null, null, null,
                        new BaseHARDumperListener.Har.Content(-1, 0, null, "{\"ok\":\"yes\"}", null, ""),
                        null, -1, -1, ""
                ),
                null, null, null, null, "");
        final var har = new BaseHARDumperListener.Har(new BaseHARDumperListener.Har.Log(
                "1.2", null, null, null, List.of(entry), ""));

        try (final var client = new HARHttpClient(new HARHttpClient.Configuration(har))) {
            final var response = client.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create("http://localhost:123456/post"))
                            .header("content-type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(123, response.statusCode());
            assertEquals(entry.response().content().text(), response.body());
        }
    }
}
