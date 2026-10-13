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
package io.yupiik.fusion.http.server.api;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Mainly a convenient API around {@code Flow.Publisher<ByteBuffer>} to ease usage.
 */
public interface Body extends Flow.Publisher<ByteBuffer> {
    /**
     * @return mark the body as being re-readable by storing the content in memory.
     */
    Body cached();

    CompletionStage<String> string();

    CompletionStage<byte[]> bytes();

    String parameter(String name);
}
