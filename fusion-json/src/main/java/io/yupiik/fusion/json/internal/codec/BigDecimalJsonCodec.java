package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;

public class BigDecimalJsonCodec implements JsonCodec<BigDecimal> {
    @Override
    public Type type() {
        return BigDecimal.class;
    }

    @Override
    public BigDecimal read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext()) {
            throw new IllegalStateException("No more token to process.");
        }
        final var event = parser.next();
        return switch (event) {
            case VALUE_NUMBER -> parser.getBigDecimal();
            case VALUE_STRING -> new BigDecimal(parser.getString());
            default -> throw new IllegalStateException("Expected VALUE_STRING or VALUE_NUMBER");
        };
    }

    @Override
    public void write(final BigDecimal value, final SerializationContext context) throws IOException {
        context.writer().write(JsonStrings.escape(value.toString()));
    }
}
