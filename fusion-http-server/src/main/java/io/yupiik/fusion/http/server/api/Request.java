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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

public interface Request extends Unwrappable {
    String scheme();

    String method();

    String path();

    String query();

    /**
     * @deprecated prefer {@link #fullBody()} instead. Kept for backward compatibility only.
     * @return same value as {@link #fullBody()}.
     */
    @Deprecated(since = "1.0.4")
    default Flow.Publisher<ByteBuffer> body() {
        return fullBody();
    }

    Body fullBody();

    Stream<Cookie> cookies();

    String parameter(String name);

    Map<String, String[]> parameters();

    String header(String name);

    Map<String, List<String>> headers();

    <T> T attribute(String key, Class<T> type);

    <T> void setAttribute(String key, T value);
}
