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
package io.yupiik.fusion.http.server.impl.servlet;

import java.nio.ByteBuffer;

public interface ByteBufferPool {
    /**
     * @return a buffer usable by the request processing.
     */
    ByteBuffer get();

    /**
     * get back the buffer in the pool if needed.
     * important: this is where you call flip() or whatever reset logic you need
     * @param buffer the buffer to try to integrate to the pool
     */
    default void release(final ByteBuffer buffer) {
        // no-op
    }

    /**
     * @return true if this implementation recycle buffers.
     */
    default boolean needsRelease() {
        return false;
    }
}
