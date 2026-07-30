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
package io.yupiik.fusion.json.internal.io;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

// byte[] twin of io.yupiik.fusion.json.internal.parser.BufferProvider
public class ByteBufferProvider {
    private final int size;
    private final int max;
    private final AtomicInteger counter = new AtomicInteger();
    private final ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();

    public ByteBufferProvider(final int size, final int maxBuffers) {
        this.size = size;
        this.max = maxBuffers;
    }

    public byte[] newBuffer() {
        final var buffer = queue.poll();
        if (buffer == null) {
            return new byte[size];
        } else if (max >= 0) {
            counter.updateAndGet(v -> Math.max(0, v - 1));
        }
        return buffer;
    }

    public void release(final byte[] value) {
        if (max < 0) {
            queue.offer(value);
            return;
        }
        if (counter.getAndUpdate(operand -> {
            final var incr = operand + 1;
            return Math.min(max, incr);
        }) < max) {
            queue.offer(value);
        }
    }
}
