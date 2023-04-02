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
package io.yupiik.fusion.http.server.impl.io;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RequestBodyAggregator implements Flow.Subscriber<ByteBuffer> {
    private final List<ByteBuffer> aggregated = new ArrayList<>();
    private final CompletableFuture<char[]> future = new CompletableFuture<>();
    private final Charset charset;

    public RequestBodyAggregator(final Flow.Publisher<ByteBuffer> publisher) { // backward compatibility
        this(publisher, UTF_8);
    }

    public RequestBodyAggregator(final Flow.Publisher<ByteBuffer> publisher,
                                 final Charset charset) {
        this.charset = charset;
        publisher.subscribe(this);
    }

    public CompletionStage<char[]> promise() {
        return future;
    }

    @Override
    public void onSubscribe(final Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(final ByteBuffer item) {
        synchronized (aggregated) {
            aggregated.add(item);
        }
    }

    @Override
    public void onError(final Throwable throwable) {
        future.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        final byte[] res;
        synchronized (aggregated) {
            res = new byte[aggregated.stream().mapToInt(ByteBuffer::remaining).sum()];
            int start = 0;
            for (final var array : aggregated) {
                final int remaining = array.remaining();
                array.get(res, start, remaining);
                start += remaining;
            }
        }
        var decoded = charset.decode(ByteBuffer.wrap(res)).compact();
        if (decoded.limit() != decoded.capacity()) {
            final var tmp = new char[decoded.limit()];
            System.arraycopy(decoded.array(), 0, tmp, 0, decoded.limit());
            decoded = CharBuffer.wrap(tmp);
        }
        future.complete(decoded.array());
    }
}
