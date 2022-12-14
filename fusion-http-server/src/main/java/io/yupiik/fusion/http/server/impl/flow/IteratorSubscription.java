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
