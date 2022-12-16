package io.yupiik.fusion.framework.processor.meta;

import io.yupiik.fusion.json.internal.codec.ObjectJsonCodec;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

public interface GenericObjectJsonSerializationLike {
    Map<String, Object> asMap();

    default String toJson() {
        final var jsonCodec = new ObjectJsonCodec();
        final var writer = new StringWriter();
        try (writer) {
            jsonCodec.write(asMap(), new JsonCodec.SerializationContext(writer, k -> {
                throw new IllegalArgumentException("unsupported type: '" + k + "'");
            }));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return writer.toString();
    }
}
