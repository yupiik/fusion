package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.parser.JsonParser;

public class IntegerJsonCodec extends NumberJsonCodec<Integer> {
    public IntegerJsonCodec() {
        super(Integer.class);
    }

    @Override
    protected Integer read(final JsonParser parser) {
        return parser.getInt();
    }
}
