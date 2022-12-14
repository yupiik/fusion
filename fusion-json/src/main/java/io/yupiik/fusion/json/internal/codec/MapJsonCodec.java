package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.yupiik.fusion.json.internal.parser.JsonParser.Event.END_OBJECT;
import static io.yupiik.fusion.json.internal.parser.JsonParser.Event.KEY_NAME;
import static io.yupiik.fusion.json.internal.parser.JsonParser.Event.START_OBJECT;

public class MapJsonCodec<A> implements JsonCodec<Map<String, A>> {
    private final JsonCodec<A> delegate;
    private final Type type;

    public MapJsonCodec(final JsonCodec<A> delegate) {
        this.delegate = delegate;
        this.type = new Types.ParameterizedTypeImpl(Map.class, String.class, delegate.type());
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public Map<String, A> read(final DeserializationContext context) throws IOException {
        final var reader = context.parser();
        reader.enforceNext(START_OBJECT);

        final var instance = new LinkedHashMap<String, A>();
        JsonParser.Event event;
        while (reader.hasNext() && (event = reader.next()) != END_OBJECT) {
            reader.rewind(event);

            final var keyEvent = reader.next();
            if (keyEvent != KEY_NAME) {
                throw new IllegalStateException("Expected=KEY_NAME, but got " + keyEvent);
            }
            instance.put(reader.getString(), delegate.read(context));
        }
        return instance;
    }

    @Override
    public void write(final Map<String, A> value, final SerializationContext context) throws IOException {
        final var writer = context.writer();
        final var it = value.entrySet().iterator();
        writer.write('{');
        while (it.hasNext()) {
            final var entry = it.next();
            if (entry == null) {
                continue;
            }

            writer.write(JsonStrings.escape(entry.getKey()) + ":");
            if (entry.getValue() == null) {
                writer.write("null");
            } else {
                delegate.write(entry.getValue(), context);
            }
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write('}');
    }
}
