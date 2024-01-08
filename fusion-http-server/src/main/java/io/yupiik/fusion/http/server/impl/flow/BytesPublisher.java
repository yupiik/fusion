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
package io.yupiik.fusion.http.server.impl.flow;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BytesPublisher implements Flow.Publisher<ByteBuffer> {
    private final List<ByteBuffer> buffers;

    public BytesPublisher(final List<ByteBuffer> buffers) {
        this.buffers = buffers;
    }

    public BytesPublisher(final byte[] value) {
        this(List.of(ByteBuffer.wrap(value)));
    }

    public BytesPublisher(final String value) {
        this(value.getBytes(UTF_8));
    }

    @Override
    public void subscribe(final Flow.Subscriber<? super ByteBuffer> subscriber) {
        subscriber.onSubscribe(new IteratorSubscription<>(subscriber, buffers.iterator()));
    }
}
