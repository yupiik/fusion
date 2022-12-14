package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;

public class BooleanJsonCodec implements JsonCodec<Boolean> {
    @Override
    public Type type() {
        return Boolean.class;
    }

    @Override
    public Boolean read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext()) {
            throw new IllegalStateException("No value");
        }
        final var next = parser.next();
        return switch (next) {
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            default -> throw new IllegalStateException("Expected true/false and got " + next);
        };
    }

    @Override
    public void write(final Boolean value, final SerializationContext context) throws IOException {
        context.writer().write(String.valueOf(value));
    }
}
