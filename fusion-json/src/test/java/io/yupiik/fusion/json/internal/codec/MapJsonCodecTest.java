package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapJsonCodecTest {
    @Test
    void mapPrimitive() throws IOException {
        final var codec = new MapJsonCodec<>(new IntegerJsonCodec());
        try (final var parser = parser("{\"k\":1,\"second\":22}")) {
            assertEquals(
                    Map.of("k", 1, "second", 22),
                    codec.read(new JsonCodec.DeserializationContext(parser, c -> null)));
        }
    }

    private JsonParser parser(final String string) {
        return new JsonParser(new StringReader(string), 16, new BufferProvider(16), true);
    }
}
