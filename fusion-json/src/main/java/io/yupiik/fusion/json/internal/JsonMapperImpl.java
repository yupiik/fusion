/*
 * Copyright (c) 2022 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.fusion.json.internal;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.Types;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.codec.BigDecimalJsonCodec;
import io.yupiik.fusion.json.internal.codec.BooleanJsonCodec;
import io.yupiik.fusion.json.internal.codec.CollectionJsonCodec;
import io.yupiik.fusion.json.internal.codec.DoubleJsonCodec;
import io.yupiik.fusion.json.internal.codec.EnumJsonCodec;
import io.yupiik.fusion.json.internal.codec.IntegerJsonCodec;
import io.yupiik.fusion.json.internal.codec.LocalDateJsonCodec;
import io.yupiik.fusion.json.internal.codec.LocalDateTimeJsonCodec;
import io.yupiik.fusion.json.internal.codec.LongJsonCodec;
import io.yupiik.fusion.json.internal.codec.MapJsonCodec;
import io.yupiik.fusion.json.internal.codec.ObjectJsonCodec;
import io.yupiik.fusion.json.internal.codec.OffsetDateTimeJsonCodec;
import io.yupiik.fusion.json.internal.codec.StringJsonCodec;
import io.yupiik.fusion.json.internal.codec.ZonedDateTimeJsonCodec;
import io.yupiik.fusion.json.internal.io.FastStringWriter;
import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.patch.JsonPatchOperation;
import io.yupiik.fusion.json.serialization.ExtendedWriter;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.json.spi.Parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

public class JsonMapperImpl implements JsonMapper {
    private final Map<Type, JsonCodec<?>> codecs;
    private final Function<Reader, Parser> parserFactory;
    private final boolean serializeNulls;

    protected JsonMapperImpl(final Map<Type, JsonCodec<?>> codecs, final Function<Reader, Parser> parserFactory, final boolean serializeNulls) {
        this.codecs = codecs;
        this.parserFactory = parserFactory;
        this.serializeNulls = serializeNulls;
    }

    public JsonMapperImpl(final Collection<JsonCodec<?>> jsonCodecs, final Configuration configuration) {
        this(jsonCodecs, configuration, createReaderParserFunction(configuration));
    }

    public JsonMapperImpl(final Collection<JsonCodec<?>> jsonCodecs, final Configuration configuration,
                          final Function<Reader, Parser> readerParserFunction) {
        this.parserFactory = readerParserFunction;
        this.serializeNulls = false;

        this.codecs = new ConcurrentHashMap<>();
        this.codecs.putAll(toCodecMap(jsonCodecs.stream()));
        this.codecs.putAll(toCodecMap(builtInCodecs().filter(it -> !this.codecs.containsKey(it.type()))));
        if (!this.codecs.containsKey(Map.class)) {
            final var object = this.codecs.get(Object.class);
            if (object != null) {
                this.codecs.put(Map.class, object);
            }
        }
    }

    protected Stream<JsonCodec<?>> builtInCodecs() {
        return Stream.of( // do not forget to update io.yupiik.fusion.framework.processor.internal.generator.JsonCodecGenerator if changing this
                new StringJsonCodec(),
                new IntegerJsonCodec(),
                new LongJsonCodec(),
                new DoubleJsonCodec(),
                new BigDecimalJsonCodec(),
                new BooleanJsonCodec(),
                new LocalDateJsonCodec(),
                new LocalDateTimeJsonCodec(),
                new ZonedDateTimeJsonCodec(),
                new OffsetDateTimeJsonCodec(),
                new ObjectJsonCodec(),
                new EnumJsonCodec<>(JsonPatchOperation.Operation.class, List.of(JsonPatchOperation.Operation.add, JsonPatchOperation.Operation.copy, JsonPatchOperation.Operation.move, JsonPatchOperation.Operation.remove, JsonPatchOperation.Operation.replace, JsonPatchOperation.Operation.test), Enum::name),
                new JsonPatchOperation.Codec());
    }

    @Override
    public <T> Optional<T> as(final Class<T> type) {
        if (type == Configuring.class) {
            return Optional.of(type.cast(new ConfiguringImpl(this)));
        }
        return JsonMapper.super.as(type);
    }

    @Override
    public <A> byte[] toBytes(final A instance) {
        final var out = new ByteArrayOutputStream();
        try (final var writer = new OutputStreamWriter(out, UTF_8)) {
            write(instance, writer);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    @Override
    public <A> A fromBytes(final Class<A> type, final byte[] bytes) {
        return fromBytes((Type) type, bytes);
    }

    @Override
    public <A> A fromBytes(final Type type, final byte[] bytes) {
        try (final var reader = new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8)) {
            return read(type, reader);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public <A> A fromString(final Class<A> type, final String string) {
        return fromString((Type) type, string);
    }

    @Override
    public <A> A fromString(final Type type, final String string) {
        try (final var reader = new StringReader(string)) {
            return read(type, reader);
        }
    }

    @Override
    public <A> String toString(final A instance) {
        final var writer = new FastStringWriter(new StringBuilder());
        try (writer) {
            write(instance, writer);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return writer.toString();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <A> void write(final A instance, final Writer writer) {
        try {
            if (instance == null) {
                writer.write("null");
                return;
            }

            if (instance instanceof Collection<?> collection) {
                doWriteCollection(writer, collection);
                return;
            }

            if (instance instanceof Map<?, ?> map) {
                if (map.isEmpty()) {
                    writer.write("{}");
                    return;
                }

                final var entry = map.entrySet().stream()
                        .filter(it -> it.getValue() != null)
                        .findFirst()
                        .orElse(null);
                if (entry != null && entry.getKey() instanceof String && entry.getValue() != null) {
                    if (entry.getValue() instanceof Map<?, ?>) { // consider it is just an object
                        final JsonCodec jsonCodec = codecs.get(Object.class);
                        jsonCodec.write(map, newSerializationContext(writer));
                        return;
                    }

                    final var itemClass = entry.getValue().getClass();
                    // if at least one element does not match the type of the first item don't optimise it and go through object codec
                    if (map.values().stream().filter(Objects::nonNull).anyMatch(it -> !itemClass.isInstance(it))) {
                        final JsonCodec jsonCodec = codecs.get(Object.class);
                        jsonCodec.write(map, newSerializationContext(writer));
                        return;
                    }

                    final var key = new Types.ParameterizedTypeImpl(Map.class, String.class, itemClass);
                    final JsonCodec existing = codecs.get(key);
                    if (existing != null) {
                        existing.write(map, newSerializationContext(writer));
                        return;
                    }

                    var itemCodec = (JsonCodec<?>) codecs.get(itemClass);
                    if (itemCodec == null) {
                        if (entry.getValue() instanceof Collection<?> coll) {
                            if (coll.isEmpty()) {
                                itemCodec = new CollectionJsonCodec<>(codecs.get(Object.class), Object.class, ArrayList::new);
                            } else {
                                final var type = coll.iterator().next();
                                if (type == null) {
                                    itemCodec = new CollectionJsonCodec<>(codecs.get(Object.class), Object.class, ArrayList::new);
                                } else {
                                    var clazz = type.getClass();
                                    JsonCodec<?> nestedCodec = codecs.get(clazz);
                                    if (nestedCodec == null && clazz.getName().startsWith("java.util.")) {
                                        nestedCodec = codecs.get(Object.class);
                                    }
                                    if (nestedCodec == null) {
                                        throw missingCodecException(clazz);
                                    }
                                    itemCodec = new CollectionJsonCodec<>(nestedCodec, clazz, ArrayList::new);
                                }
                            }
                        } else {
                            throw missingCodecException(itemClass);
                        }
                    }
                    final var wrapper = new MapJsonCodec<>(itemCodec);
                    codecs.putIfAbsent(key, wrapper);
                    wrapper.write((Map) map, newSerializationContext(writer));
                    return;
                }

                final var wrapped = wrap(writer);
                final var keys = map.keySet().iterator();
                wrapped.write('{');
                while (keys.hasNext()) {
                    wrapped.write('"');
                    wrapped.write(JsonStrings.escapeChars(String.valueOf(keys.hasNext())));
                    wrapped.write('"');
                    wrapped.write(':');
                    wrapped.write('n');
                    wrapped.write('u');
                    wrapped.write('l');
                    wrapped.write('l');
                    if (keys.hasNext()) {
                        wrapped.write(',');
                    }
                }
                wrapped.write('}');
                return;
            }

            final var clazz = instance.getClass();
            final var codec = (JsonCodec<A>) codecs.get(clazz);
            if (codec == null) {
                throw missingCodecException(clazz);
            }

            codec.write(instance, newSerializationContext(writer));
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private JsonCodec.SerializationContext newSerializationContext(final Writer writer) {
        return new JsonCodec.SerializationContext(wrap(writer), this::codecLookup, serializeNulls);
    }

    @SuppressWarnings("unchecked")
    private void doWriteCollection(final Writer writer, final Collection<?> collection) throws IOException {
        if (collection.isEmpty()) {
            writer.write("[]");
            return;
        }

        final var firstItem = collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        if (firstItem != null) {
            if (firstItem instanceof Map<?, ?>) { // consider it is just an object
                final JsonCodec jsonCodec = codecs.get(Object.class);
                jsonCodec.write(collection, newSerializationContext(writer));
                return;
            }

            final var itemClass = firstItem.getClass();

            final var key = new Types.ParameterizedTypeImpl(Collection.class, itemClass);
            final JsonCodec existing = codecs.get(key);
            if (existing != null) {
                existing.write(collection, newSerializationContext(writer));
                return;
            }

            final var itemCodec = (JsonCodec<?>) codecs.get(itemClass);
            if (itemCodec == null) {
                throw missingCodecException(itemClass);
            }

            final var wrapper = new CollectionJsonCodec<>(itemCodec, List.class, () -> (Collection) new ArrayList<>());
            codecs.putIfAbsent(key, wrapper);
            wrapper.write(collection, newSerializationContext(writer));
            return;
        }

        writer.write(collection.stream().map(it -> "null").collect(joining(",", "[", "]")));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A read(final Type type, final Reader rawReader) {
        try (final var reader = parserFactory.apply(rawReader)) {
            final var codec = (JsonCodec<A>) codecs.get(type);
            if (codec == null) {
                if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> rawClass) {
                    if (rawClass == Map.class && pt.getActualTypeArguments().length == 2 && pt.getActualTypeArguments()[0] == String.class) {
                        final var delegate = codecs.get(pt.getActualTypeArguments()[1]);
                        if (delegate == null) {
                            throw missingCodecException(pt.getActualTypeArguments()[0]);
                        }
                        final var wrapper = new MapJsonCodec<>(delegate);
                        codecs.putIfAbsent(wrapper.type(), wrapper);
                        return (A) wrapper.read(new JsonCodec.DeserializationContext(reader, this::codecLookup));
                    }
                    if ((rawClass == List.class || rawClass == Collection.class) && pt.getActualTypeArguments().length == 1) {
                        final var delegate = codecs.get(pt.getActualTypeArguments()[0]);
                        if (delegate == null) {
                            throw missingCodecException(pt.getActualTypeArguments()[0]);
                        }
                        final var wrapper = new CollectionJsonCodec<>(delegate, List.class, ArrayList::new);
                        codecs.putIfAbsent(wrapper.type(), wrapper);
                        return (A) wrapper.read(new JsonCodec.DeserializationContext(reader, this::codecLookup));
                    }
                    if (rawClass == Set.class && pt.getActualTypeArguments().length == 2) {
                        final var delegate = codecs.get(pt.getActualTypeArguments()[0]);
                        if (delegate == null) {
                            throw missingCodecException(pt.getActualTypeArguments()[0]);
                        }
                        final var wrapper = new CollectionJsonCodec<>(delegate, Set.class, HashSet::new);
                        codecs.putIfAbsent(wrapper.type(), wrapper);
                        return (A) wrapper.read(new JsonCodec.DeserializationContext(reader, this::codecLookup));
                    }
                }
                throw missingCodecException(type);
            }

            return codec.read(new JsonCodec.DeserializationContext(reader, this::codecLookup));
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    // just a convenient method for typing
    @Override
    @SuppressWarnings("unchecked")
    public <A> A read(final Class<A> type, final Reader reader) {
        final var codec = (JsonCodec<A>) codecs.get(type);
        if (codec == null) {
            throw missingCodecException(type);
        }
        try (final var jsonReader = parserFactory.apply(reader)) {
            return codec.read(new JsonCodec.DeserializationContext(jsonReader, this::codecLookup));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        final var error = new IllegalStateException("Can't close some codec");
        codecs.values().stream()
                .filter(AutoCloseable.class::isInstance)
                .map(AutoCloseable.class::cast)
                .forEach(close -> {
                    try {
                        close.close();
                    } catch (final Exception e) {
                        error.addSuppressed(e);
                    }
                });
        if (error.getSuppressed().length > 0) {
            throw error;
        }
    }

    private Map<Type, JsonCodec<?>> toCodecMap(final Stream<JsonCodec<?>> codecStream) {
        return codecStream.collect(toMap(JsonCodec::type, identity()));
    }

    private JsonCodec<?> codecLookup(final Class<?> type) {
        return codecs.get(type);
    }

    private IllegalStateException missingCodecException(final Type type) {
        return new IllegalStateException("No codec for '" + type.getTypeName() + "', did you forget to mark it @JsonModel");
    }

    private ExtendedWriter wrap(final Writer writer) {
        return writer instanceof ExtendedWriter ew ? ew : new ExtendedWriter(writer);
    }

    private static Function<Reader, Parser> createReaderParserFunction(final Configuration configuration) {
        final int maxStringLength = configuration.get("fusion.json.maxStringLength")
                .map(Integer::parseInt)
                .orElse(8 * 1024);
        final boolean autoAdjust = configuration.get("fusion.json.bufferAutoAdjust")
                .map(Boolean::parseBoolean)
                .orElse(true);
        final int maxBuffers = configuration.get("fusion.json.maxBuffers")
                .map(Integer::parseInt)
                .orElse(-1);
        final var bufferFactory = new BufferProvider(maxStringLength, maxBuffers);
        return reader -> new JsonParser(reader, maxStringLength, bufferFactory, autoAdjust);
    }

    private static class ConfiguringImpl implements Configuring {
        private final JsonMapperImpl parent;
        private boolean serializeNulls;

        private ConfiguringImpl(final JsonMapperImpl jsonMapper) {
            this.parent = jsonMapper;
            this.serializeNulls = parent.serializeNulls;
        }

        @Override
        public Configuring serializeNulls() {
            this.serializeNulls = true;
            return this;
        }

        @Override
        public JsonMapper build() {
            return new JsonMapperImpl(parent.codecs, parent.parserFactory, serializeNulls);
        }
    }
}
