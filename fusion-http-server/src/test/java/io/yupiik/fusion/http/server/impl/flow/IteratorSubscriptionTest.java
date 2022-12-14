package io.yupiik.fusion.http.server.impl.flow;

import io.yupiik.fusion.http.server.impl.ByteBuffers;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IteratorSubscriptionTest {
    @Test
    void consume() {
        final var stringifier = new Stringifier();
        final var iterator = new IteratorSubscription<>(
                stringifier,
                Stream.of("first", "second", "third")
                        .map(it -> ByteBuffer.wrap(it.getBytes(UTF_8)))
                        .iterator());
        iterator.request(3);
        assertTrue(stringifier.completed);
        assertNull(stringifier.error, () -> stringifier.error.getMessage());
        assertEquals("firstsecondthird", ByteBuffers.asString(stringifier.list));
    }

    private static class Stringifier implements Flow.Subscriber<ByteBuffer> {
        private final List<ByteBuffer> list = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onSubscribe(final Flow.Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(final ByteBuffer item) {
            list.add(item);
        }

        @Override
        public void onError(final Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }
}
