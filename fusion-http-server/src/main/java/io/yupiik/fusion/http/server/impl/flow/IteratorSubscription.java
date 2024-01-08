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

import java.util.Iterator;
import java.util.concurrent.Flow;

public class IteratorSubscription<S> implements Flow.Subscription {
    private final Flow.Subscriber<? super S> subscriber;
    private final Iterator<S> remaining;

    private volatile boolean cancelled;

    public IteratorSubscription(final Flow.Subscriber<? super S> subscriber, final Iterator<S> remaining) {
        this.subscriber = subscriber;
        this.remaining = remaining;
    }

    @Override
    public synchronized void request(final long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Invalid request: " + n + ", should be > 0");
        }
        if (cancelled) {
            return;
        }

        try {
            long loop = n;
            while (loop > 0 && remaining.hasNext()) {
                subscriber.onNext(remaining.next());
                loop--;
            }
            if (!remaining.hasNext()) {
                subscriber.onComplete();
            }
        } catch (final RuntimeException re) {
            subscriber.onError(re);
        }
    }

    @Override
    public synchronized void cancel() {
        cancelled = true;
    }
}
