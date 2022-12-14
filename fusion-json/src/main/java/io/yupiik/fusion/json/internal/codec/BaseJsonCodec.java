package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

// intended to host utilities for generation if needed (to reduce generated code source size)
public abstract class BaseJsonCodec<A> implements JsonCodec<A> {
    protected final Type type;

    protected BaseJsonCodec(final Type type) {
        this.type = type;
    }

    @Override
    public Type type() {
        return type;
    }

    protected void writeJsonOthers(final Map<String, Object> others, final SerializationContext context) throws IOException {
        final var delegate = context.codec(Object.class);
        final var writer = context.writer();
        final var it = others.entrySet().iterator();
        while (it.hasNext()) {
            final var entry = it.next();
            if (entry.getValue() == null) {
                continue;
            }
            writer.write(JsonStrings.escape(entry.getKey()) + ':');
            delegate.write(entry.getValue(), context);
            if (it.hasNext()) {
                writer.write(',');
            }
        }
    }
}
