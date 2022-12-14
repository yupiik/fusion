package io.yupiik.fusion.http.server.impl.flow;

import io.yupiik.fusion.http.server.impl.ByteBuffers;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriterPublisherTest {
    @Test
    void subscribe1() throws InterruptedException {
        final var publisher = new WriterPublisher(w -> {
            try (w) {
                w.write("{\"hello\":\"test\"}");
            }
        });
        final var subscriber = new Subscribe(Integer.MAX_VALUE, 0);
        publisher.subscribe(subscriber);
        assertTrue(subscriber.latch.await(1, MINUTES));
        assertNull(subscriber.error);
        assertEquals("{\"hello\":\"test\"}", ByteBuffers.asString(subscriber.buffers));
        assertEquals(1, subscriber.buffers.size());
    }

    @Test
    void subscribeChunks() throws InterruptedException {
        final var publisher = new WriterPublisher(w -> {
            try (w) {
                w.write("{\"hello\"");
                w.write(":\"test\"");
                w.write("}");
            }
        });
        final var subscriber = new Subscribe(Integer.MAX_VALUE, 0);
        publisher.subscribe(subscriber);
        assertTrue(subscriber.latch.await(1, MINUTES));
        assertNull(subscriber.error);
        assertEquals("{\"hello\":\"test\"}", ByteBuffers.asString(subscriber.buffers));
        assertEquals(3, subscriber.buffers.size());
    }

    @Test
    void subscribeIterating() throws InterruptedException {
        final var publisher = new WriterPublisher(w -> {
            try (w) {
                w.write("{\"hello\"");
                w.write(":\"test\"");
                w.write("}");
            }
        });
        final var subscriber = new Subscribe(1, 1);
        publisher.subscribe(subscriber);
        assertTrue(subscriber.latch.await(1, MINUTES));
        assertNull(subscriber.error);
        assertEquals("{\"hello\":\"test\"}", ByteBuffers.asString(subscriber.buffers));
        assertEquals(3, subscriber.buffers.size());
    }

    private static class Subscribe implements Flow.Subscriber<ByteBuffer> {
        private final int initialRequest;
        private final int onItemRequest;

        private final List<ByteBuffer> buffers = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch = new CountDownLatch(1);
        private Throwable error;
        private Flow.Subscription subscription;

        private Subscribe(final int initialRequest, final int onItemRequest) {
            this.initialRequest = initialRequest;
            this.onItemRequest = onItemRequest;
        }

        @Override
        public void onSubscribe(final Flow.Subscription subscription) {
            this.subscription = subscription;
            this.subscription.request(initialRequest);
        }

        @Override
        public void onNext(final ByteBuffer item) {
            buffers.add(item);
            if (onItemRequest > 0) {
                subscription.request(onItemRequest);
            }
        }

        @Override
        public void onError(final Throwable throwable) {
            error = throwable;
            latch.countDown();
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }
    }
}
