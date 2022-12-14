package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.parser.JsonParser;

public class DoubleJsonCodec extends NumberJsonCodec<Double> {
    public DoubleJsonCodec() {
        super(Double.class);
    }

    @Override
    protected Double read(final JsonParser parser) {
        return parser.getDouble();
    }
}
