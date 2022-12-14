package io.yupiik.fusion.json.serialization;

import io.yupiik.fusion.json.internal.parser.JsonParser;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.function.Function;

public interface JsonCodec<A> {
    Type type();

    A read(DeserializationContext context) throws IOException;

    void write(A value, SerializationContext writer) throws IOException;

    class SerializationContext {
        private final Writer writer;
        private final Function<Class<?>, JsonCodec<?>> codecLookup;

        public SerializationContext(final Writer writer, final Function<Class<?>, JsonCodec<?>> codecLookup) {
            this.writer = writer;
            this.codecLookup = codecLookup;
        }

        public Writer writer() {
            return writer;
        }

        @SuppressWarnings("unchecked")
        public <A> JsonCodec<A> codec(final Class<A> clazz) {
            return (JsonCodec<A>) codecLookup.apply(clazz);
        }
    }

    class DeserializationContext {
        private final JsonParser parser;
        private final Function<Class<?>, JsonCodec<?>> codecLookup;

        public DeserializationContext(final JsonParser parser, final Function<Class<?>, JsonCodec<?>> codecLookup) {
            this.parser = parser;
            this.codecLookup = codecLookup;
        }

        public JsonParser parser() {
            return parser;
        }

        @SuppressWarnings("unchecked")
        public <A> JsonCodec<A> codec(final Class<A> clazz) {
            return (JsonCodec<A>) codecLookup.apply(clazz);
        }
    }
}
