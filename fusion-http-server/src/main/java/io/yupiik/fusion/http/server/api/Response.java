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
package io.yupiik.fusion.http.server.api;

import io.yupiik.fusion.http.server.impl.FusionResponse;

import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

public interface Response {
    int status();

    Map<String, List<String>> headers();

    List<Cookie> cookies();

    Flow.Publisher<ByteBuffer> body();

    interface Builder {
        Builder status(int value);

        Builder header(String key, String value);

        /**
         * Add a cookie to the response, use {@code Cookie.of()} to build it.
         * @param cookie the cookie to add to the response.
         * @return this.
         */
        Builder cookie(Cookie cookie);

        Builder body(Flow.Publisher<ByteBuffer> writer);

        Builder body(String body);

        Builder body(IOConsumer<Writer> bodyHandler);

        Response build();
    }

    static Response.Builder of() {
        return new FusionResponse.Builder();
    }
}
