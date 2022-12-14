package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.parser.JsonParser;

public class LongJsonCodec extends NumberJsonCodec<Long> {
    public LongJsonCodec() {
        super(Long.class);
    }

    @Override
    protected Long read(final JsonParser parser) {
        return parser.getLong();
    }
}
