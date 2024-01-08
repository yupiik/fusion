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
package io.yupiik.kubernetes.operator.base.test;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MockController {
    public final HttpExchange writer;

    public MockController(final HttpExchange writer) {
        this.writer = writer;
    }

    public void sendEvent(final String event) {
        try {
            final var responseBody = writer.getResponseBody();
            responseBody.write((event.replace("\n", "") + '\n').getBytes(StandardCharsets.UTF_8));
            responseBody.flush();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void close() {
        writer.close();
    }
}
