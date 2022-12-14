package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;

import static io.yupiik.fusion.json.internal.parser.JsonParser.Event.VALUE_STRING;

public class ZonedDateTimeJsonCodec implements JsonCodec<ZonedDateTime> {
    @Override
    public Type type() {
        return ZonedDateTime.class;
    }

    @Override
    public ZonedDateTime read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext() || parser.next() != VALUE_STRING) {
            throw new IllegalStateException("Expected VALUE_STRING");
        }
        return ZonedDateTime.parse(parser.getString());
    }

    @Override
    public void write(final ZonedDateTime value, final SerializationContext context) throws IOException {
        context.writer().write(JsonStrings.escape(value.toString()));
    }
}
