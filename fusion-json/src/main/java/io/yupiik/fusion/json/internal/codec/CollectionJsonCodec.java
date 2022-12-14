package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.function.Supplier;

import static io.yupiik.fusion.json.internal.parser.JsonParser.Event.END_ARRAY;
import static io.yupiik.fusion.json.internal.parser.JsonParser.Event.START_ARRAY;

public class CollectionJsonCodec<A, L extends Collection<A>> implements JsonCodec<L> {
    private final JsonCodec<A> delegate;
    private final Supplier<L> collectionFactory;
    private final Type type;

    public CollectionJsonCodec(final JsonCodec<A> delegate, final Class<?> rawType, final Supplier<L> collectionFactory) {
        this.delegate = delegate;
        this.collectionFactory = collectionFactory;
        this.type = new Types.ParameterizedTypeImpl(rawType, delegate.type());
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public L read(final DeserializationContext context) throws IOException {
        final var reader = context.parser();
        if (!reader.hasNext()) {
            throw new IllegalStateException("No more element");
        }

        final var next = reader.next();
        if (next != START_ARRAY) {
            throw new IllegalStateException("Expected=START_ARRAY, but got " + next);
        }

        final var instance = collectionFactory.get();
        JsonParser.Event event;
        while (reader.hasNext() && (event = reader.next()) != END_ARRAY) {
            reader.rewind(event);
            instance.add(delegate.read(context));
        }

        return instance;
    }

    @Override
    public void write(final L value, final SerializationContext context) throws IOException {
        final var writer = context.writer();
        final var it = value.iterator();
        writer.write('[');
        while (it.hasNext()) {
            final var next = it.next();
            if (next == null) {
                writer.write("null");
            } else {
                delegate.write(next, context);
            }
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write(']');
    }
}
