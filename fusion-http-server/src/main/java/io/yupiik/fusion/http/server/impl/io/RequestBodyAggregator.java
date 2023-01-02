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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RequestBodyAggregator implements Flow.Subscriber<ByteBuffer> {
    private final List<ByteBuffer> aggregated = new ArrayList<>();
    private final CompletableFuture<String> future = new CompletableFuture<>();

    public RequestBodyAggregator(final Flow.Publisher<ByteBuffer> publisher) {
        publisher.subscribe(this);
    }

    public CompletionStage<String> promise() {
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
        future.complete(new String(res, UTF_8));
    }
}
