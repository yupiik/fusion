package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;

import static io.yupiik.fusion.json.internal.parser.JsonParser.Event.VALUE_NUMBER;

public abstract class NumberJsonCodec<A> implements JsonCodec<A> {
    private final Class<A> type;

    public NumberJsonCodec(final Class<A> type) {
        this.type = type;
    }

    protected abstract A read(final JsonParser parser);

    @Override
    public Type type() {
        return type;
    }

    @Override
    public A read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext()) {
            throw new IllegalStateException("No more token.");
        }
        final JsonParser.Event event = parser.next();
        if (event != VALUE_NUMBER) {
            throw new IllegalStateException("Expected VALUE_NUMBER but got: " + event);
        }
        return read(parser);
    }

    @Override
    public void write(final A value, final SerializationContext context) throws IOException {
        context.writer().write(String.valueOf(value));
    }
}
