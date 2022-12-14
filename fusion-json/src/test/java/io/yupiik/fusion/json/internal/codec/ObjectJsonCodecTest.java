package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectJsonCodecTest {
    @Test
    void object() throws IOException {
        assertCodec("{\"test\":\"ok\"}", "{test=ok}");
    }

    @Test
    void list() throws IOException {
        assertCodec("[\"test\",\"ok\"]", "[test, ok]");
    }

    private void assertCodec(final String json, final String toString) throws IOException {
        final var codec = new ObjectJsonCodec();
        try (final var parser = parser(json)) {
            final var read = codec.read(new JsonCodec.DeserializationContext(parser, c -> null));
            assertEquals(toString, read.toString());

            final var out = new StringWriter();
            codec.write(read, new JsonCodec.SerializationContext(out, c -> null));
            assertEquals(json, out.toString());
        }
    }

    private JsonParser parser(final String string) {
        return new JsonParser(new StringReader(string), 16, new BufferProvider(16), true);
    }
}
