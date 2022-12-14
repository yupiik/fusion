package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDateTime;

import static io.yupiik.fusion.json.internal.parser.JsonParser.Event.VALUE_STRING;

public class LocalDateTimeJsonCodec implements JsonCodec<LocalDateTime> {
    @Override
    public Type type() {
        return LocalDateTime.class;
    }

    @Override
    public LocalDateTime read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext() || parser.next() != VALUE_STRING) {
            throw new IllegalStateException("Expected VALUE_STRING");
        }
        return LocalDateTime.parse(parser.getString());
    }

    @Override
    public void write(final LocalDateTime value, final SerializationContext context) throws IOException {
        context.writer().write(JsonStrings.escape(value.toString()));
    }
}
