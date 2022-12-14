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
