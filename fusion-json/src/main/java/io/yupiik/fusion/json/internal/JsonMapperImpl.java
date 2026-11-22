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
import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.deserialization.AvailableCharArrayReader;
import io.yupiik.fusion.json.internal.codec.BigDecimalJsonCodec;
import io.yupiik.fusion.json.internal.codec.BooleanJsonCodec;
import io.yupiik.fusion.json.internal.codec.CollectionJsonCodec;
import io.yupiik.fusion.json.internal.codec.DoubleJsonCodec;
import io.yupiik.fusion.json.internal.codec.EnumJsonCodec;
import io.yupiik.fusion.json.internal.codec.FloatJsonCodec;
import io.yupiik.fusion.json.internal.codec.GenericNumberJsonCodec;
import io.yupiik.fusion.json.internal.codec.IntegerJsonCodec;
import io.yupiik.fusion.json.internal.codec.LocalDateJsonCodec;
import io.yupiik.fusion.json.internal.codec.LocalDateTimeJsonCodec;
import io.yupiik.fusion.json.internal.codec.LongJsonCodec;
import io.yupiik.fusion.json.internal.codec.MapJsonCodec;
import io.yupiik.fusion.json.internal.codec.ObjectJsonCodec;
import io.yupiik.fusion.json.internal.codec.OffsetDateTimeJsonCodec;
import io.yupiik.fusion.json.internal.codec.RawMapJsonCodec;
import io.yupiik.fusion.json.internal.codec.StringJsonCodec;
import io.yupiik.fusion.json.internal.codec.ZonedDateTimeJsonCodec;
import io.yupiik.fusion.json.internal.io.BufferedExtendedWriter;
import io.yupiik.fusion.json.internal.io.ByteBufferProvider;
import io.yupiik.fusion.json.internal.io.FastStringWriter;
import io.yupiik.fusion.json.internal.io.FastUtf8Reader;
import io.yupiik.fusion.json.internal.parser.BufferProvider;
import io.yupiik.fusion.json.internal.parser.DocumentParser;
import io.yupiik.fusion.json.internal.parser.JsonParser;
import io.yupiik.fusion.json.internal.parser.ValidatingParser;
import io.yupiik.fusion.json.patch.JsonPatchOperation;
import io.yupiik.fusion.json.serialization.ExtendedWriter;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.json.spi.Parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final CollectionJsonCodec<Object, Collection<Object>> dynamicCollectionCodec =
            new CollectionJsonCodec<>(null, Collection.class, ArrayList::new);
    private final Map<Type, JsonCodec<?>> codecs;
    private final Map<Class<?>, JsonCodec<?>> collectionOverrides;
    private final Function<Reader, Parser> parserFactory;
    private final boolean serializeNulls;
    private final boolean ignoreCodecClose;
    private final boolean rejectIncompatibleValues;
    private final boolean rejectDuplicateKeys;
    private final boolean rejectTrailingTokens;
    private final int maxDepth;
    private final boolean rejectUnknownFields;
    private final Function<Class<?>, JsonCodec<?>> codecLookup = this::codecLookup; // avoids a lambda instance per (de)serialization
    private final BufferProvider writeBuffers;
    private final ByteBufferProvider byteBuffers;

    protected JsonMapperImpl(final Map<Type, JsonCodec<?>> codecs, final Function<Reader, Parser> parserFactory, final boolean serializeNulls, final boolean ignoreCodecClose) {
        this(codecs, parserFactory, serializeNulls, ignoreCodecClose, true);
    }

    protected JsonMapperImpl(final Map<Type, JsonCodec<?>> codecs, final Function<Reader, Parser> parserFactory,
                             final boolean serializeNulls, final boolean ignoreCodecClose, final boolean rejectIncompatibleValues) {
        this(codecs, parserFactory, serializeNulls, ignoreCodecClose, rejectIncompatibleValues, false);
    }

    protected JsonMapperImpl(final Map<Type, JsonCodec<?>> codecs, final Function<Reader, Parser> parserFactory,
                             final boolean serializeNulls, final boolean ignoreCodecClose, final boolean rejectIncompatibleValues,
                             final boolean rejectDuplicateKeys) {
        this(codecs, parserFactory, serializeNulls, ignoreCodecClose, rejectIncompatibleValues, rejectDuplicateKeys, true);
    }

    protected JsonMapperImpl(final Map<Type, JsonCodec<?>> codecs, final Function<Reader, Parser> parserFactory,
                             final boolean serializeNulls, final boolean ignoreCodecClose, final boolean rejectIncompatibleValues,
                             final boolean rejectDuplicateKeys, final boolean rejectTrailingTokens) {
        this(codecs, parserFactory, serializeNulls, ignoreCodecClose, rejectIncompatibleValues,
                rejectDuplicateKeys, rejectTrailingTokens, 0);
    }

    protected JsonMapperImpl(final Map<Type, JsonCodec<?>> codecs, final Function<Reader, Parser> parserFactory,
                             final boolean serializeNulls, final boolean ignoreCodecClose, final boolean rejectIncompatibleValues,
                             final boolean rejectDuplicateKeys, final boolean rejectTrailingTokens, final int maxDepth) {
        this(codecs, parserFactory, serializeNulls, ignoreCodecClose, rejectIncompatibleValues,
                rejectDuplicateKeys, rejectTrailingTokens, maxDepth, false);
    }

    protected JsonMapperImpl(final Map<Type, JsonCodec<?>> codecs, final Function<Reader, Parser> parserFactory,
                             final boolean serializeNulls, final boolean ignoreCodecClose, final boolean rejectIncompatibleValues,
                             final boolean rejectDuplicateKeys, final boolean rejectTrailingTokens, final int maxDepth,
                             final boolean rejectUnknownFields) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be non-negative");
        }
        this.codecs = codecs;
        this.collectionOverrides = indexCollectionOverrides(codecs);
        this.parserFactory = parserFactory;
        this.serializeNulls = serializeNulls;
        this.ignoreCodecClose = ignoreCodecClose;
        this.rejectIncompatibleValues = rejectIncompatibleValues;
        this.rejectDuplicateKeys = rejectDuplicateKeys;
        this.rejectTrailingTokens = rejectTrailingTokens;
        this.maxDepth = maxDepth;
        this.rejectUnknownFields = rejectUnknownFields;
        this.writeBuffers = new BufferProvider(8 * 1024, -1);
        this.byteBuffers = new ByteBufferProvider(8 * 1024, -1);
    }

    public JsonMapperImpl(final Collection<JsonCodec<?>> jsonCodecs, final Configuration configuration) {
        this(jsonCodecs, configuration, createReaderParserFunction(configuration));
    }

    public JsonMapperImpl(final Collection<JsonCodec<?>> jsonCodecs,
                          final Configuration configuration,
                          final Function<Reader, Parser> readerParserFunction) {
        this.parserFactory = readerParserFunction;
        this.serializeNulls = false;
        this.ignoreCodecClose = false;
        this.rejectIncompatibleValues = configuration.get("fusion.json.rejectIncompatibleValues")
                .map(Boolean::parseBoolean).orElse(true);
        this.rejectDuplicateKeys = configuration.get("fusion.json.rejectDuplicateKeys")
                .map(Boolean::parseBoolean).orElse(false);
        this.rejectTrailingTokens = configuration.get("fusion.json.rejectTrailingTokens")
                .map(Boolean::parseBoolean).orElse(true);
        this.maxDepth = configuration.get("fusion.json.maxDepth").map(Integer::parseInt).orElse(0);
        this.rejectUnknownFields = configuration.get("fusion.json.rejectUnknownFields")
                .map(Boolean::parseBoolean).orElse(false);
        if (maxDepth < 0) {
            throw new IllegalArgumentException("fusion.json.maxDepth must be non-negative");
        }
        this.writeBuffers = createBufferProvider(configuration);
        this.byteBuffers = new ByteBufferProvider(maxStringLength(configuration), maxBuffers(configuration));

        this.codecs = new ConcurrentHashMap<>();
        this.codecs.putAll(toCodecMap(jsonCodecs.stream()));
        this.codecs.putAll(toCodecMap(builtInCodecs().filter(it -> !this.codecs.containsKey(it.type()))));
        this.codecs.putIfAbsent(double.class, this.codecs.get(Double.class));
        this.codecs.putIfAbsent(float.class, this.codecs.get(Float.class));
        this.codecs.putIfAbsent(int.class, this.codecs.get(Integer.class));
        this.codecs.putIfAbsent(long.class, this.codecs.get(Long.class));
        this.codecs.putIfAbsent(boolean.class, this.codecs.get(Boolean.class));
        if (!this.codecs.containsKey(Map.class)) {
            final var object = this.codecs.get(Object.class);
            if (object != null) {
                this.codecs.put(Map.class, new RawMapJsonCodec(object));
            }
        }
        this.collectionOverrides = indexCollectionOverrides(this.codecs);
    }

    protected Stream<JsonCodec<?>> builtInCodecs() {
        return Stream.of( // do not forget to update io.yupiik.fusion.framework.processor.internal.generator.JsonCodecGenerator if changing this
                new StringJsonCodec(),
                new IntegerJsonCodec(),
                new LongJsonCodec(),
                new DoubleJsonCodec(),
                new FloatJsonCodec(),
                new GenericNumberJsonCodec(),
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
        // the JDK UTF-8 String encoder is way faster than a char-by-char UTF-8 encoder
        return toString(instance).getBytes(UTF_8);
    }

    @Override
    public <A> A fromBytes(final Class<A> type, final byte[] bytes) {
        return fromBytes((Type) type, bytes);
    }

    @Override
    public <A> A fromBytes(final Type type, final byte[] bytes) {
        try (final var reader = new AvailableCharArrayReader(utf8ToChars(bytes))) {
            return read(type, reader);
        }
    }

    @Override
    public <A> A read(final Type type, final InputStream stream) {
        // FastUtf8Reader skips the InputStreamReader/StreamDecoder machinery (ASCII inflate fast path)
        return read(type, new FastUtf8Reader(stream, byteBuffers));
    }

    @Override
    public <A> A read(final Class<A> type, final InputStream stream) {
        return read(type, new FastUtf8Reader(stream, byteBuffers));
    }

    @Override
    public <A> A fromString(final Class<A> type, final String string) {
        return fromString((Type) type, string);
    }

    @Override
    public <A> A fromString(final Type type, final String string) {
        // AvailableCharArrayReader lets the parser adopt the array as its buffer (no chunked copies through StringReader)
        try (final var reader = new AvailableCharArrayReader(string.toCharArray())) {
            return read(type, reader);
        }
    }

    @Override
    public <A> String toString(final A instance) {
        final var writer = new FastStringWriter(new StringBuilder(256)); // presized, the default 16 causes several grow copies
        try (writer) {
            write(instance, writer);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return writer.toString();
    }

    @Override
    public <A> void write(final A instance, final Writer writer) {
        if (writer instanceof ExtendedWriter) { // already efficient (FastStringWriter, buffered, ...), no extra buffering
            doWrite(instance, writer);
            return;
        }
        // coalesce the many small token writes into bulk writes on the target writer
        final var buffered = new BufferedExtendedWriter(writer, writeBuffers);
        try {
            doWrite(instance, buffered);
            buffered.end();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        } finally {
            buffered.release(); // no-op when end() succeeded, on error the pooled buffer is returned without draining
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <A> void doWrite(final A instance, final Writer writer) {
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

                Map.Entry<?, ?> entry = null;
                for (final var e : map.entrySet()) {
                    if (e.getValue() != null) {
                        entry = e;
                        break;
                    }
                }
                if (entry != null && entry.getKey() instanceof String) {
                    if (entry.getValue() instanceof Map<?, ?>) { // consider it is just an object
                        final JsonCodec jsonCodec = codecs.get(Object.class);
                        jsonCodec.write(map, newSerializationContext(writer));
                        return;
                    }

                    final var itemClass = entry.getValue().getClass();
                    // if at least one element does not match the type of the first item don't optimise it and go through object codec
                    if (anyValueNotInstanceOf(map, itemClass)) {
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
                    wrapped.write(JsonStrings.escapeChars(String.valueOf(keys.next()))); // already quoted
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
        return new JsonCodec.SerializationContext(wrap(writer), codecLookup, serializeNulls);
    }

    @SuppressWarnings("unchecked")
    private void doWriteCollection(final Writer writer, final Collection<?> collection) throws IOException {
        if (!collectionOverrides.isEmpty()) {
            for (final var item : collection) {
                if (item != null) {
                    final var existing = (JsonCodec<Collection<?>>) collectionOverrides.get(item.getClass());
                    if (existing != null) {
                        existing.write(collection, newSerializationContext(writer));
                        return;
                    }
                    break;
                }
            }
        }
        dynamicCollectionCodec.write((Collection<Object>) collection, newSerializationContext(writer));
    }

    private Map<Class<?>, JsonCodec<?>> indexCollectionOverrides(final Map<Type, JsonCodec<?>> codecs) {
        final var overrides = new HashMap<Class<?>, JsonCodec<?>>();
        codecs.forEach((type, codec) -> {
            if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == Collection.class) {
                final var arguments = parameterized.getActualTypeArguments();
                if (arguments.length == 1 && arguments[0] instanceof Class<?> elementType) {
                    overrides.put(elementType, codec);
                }
            }
        });
        return Map.copyOf(overrides);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A read(final Type type, final Reader rawReader) {
        try (final var reader = newParser(rawReader)) {
            final var codec = (JsonCodec<A>) readCodec(type);
            if (codec == null) {
                throw missingCodecException(type);
            }

            return readValue(type, codec, reader);
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    @SuppressWarnings("unchecked")
    private <A> A readValue(final Type type, final JsonCodec<A> codec, final DocumentParser parser) throws IOException {
        if (!parser.hasNext()) {
            throw new JsonException("No value");
        }
        final var event = parser.next();
        if (event == Parser.Event.VALUE_NULL) {
            parser.ensureDocument(rejectTrailingTokens);
            return (A) primitiveDefault(type);
        }
        parser.rewind(event);
        final var value = codec.read(new JsonCodec.DeserializationContext(
                parser, codecLookup, rejectIncompatibleValues, rejectUnknownFields));
        parser.ensureDocument(rejectTrailingTokens);
        return value;
    }

    private Object primitiveDefault(final Type type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private JsonCodec<?> readCodec(final Type type) {
        final var existing = codecs.get(type);
        if (existing != null) {
            return existing;
        }
        if (type instanceof ParameterizedType pt) {
            final var raw = pt.getRawType();
            final var arguments = pt.getActualTypeArguments();
            final JsonCodec<?> wrapper;
            if (raw == Map.class && arguments.length == 2 && arguments[0] == String.class) {
                final var delegate = readCodec(arguments[1]);
                if (delegate == null) {
                    return null;
                }
                wrapper = new MapJsonCodec<>(delegate);
            } else if ((raw == List.class || raw == Collection.class || raw == Set.class) && arguments.length == 1) {
                final var delegate = readCodec(arguments[0]);
                if (delegate == null) {
                    return null;
                }
                wrapper = raw == Set.class ? new CollectionJsonCodec<>(delegate, Set.class, HashSet::new) :
                        new CollectionJsonCodec<>(delegate, (Class<?>) raw, ArrayList::new);
            } else {
                return null;
            }
            final var previous = codecs.putIfAbsent(type, wrapper);
            return previous == null ? wrapper : previous;
        }
        return null;
    }

    // just a convenient method for typing
    @Override
    @SuppressWarnings("unchecked")
    public <A> A read(final Class<A> type, final Reader reader) {
        try (final var jsonReader = newParser(reader)) { // close even on missing codec
            final var codec = (JsonCodec<A>) codecs.get(type);
            if (codec == null) {
                throw missingCodecException(type);
            }
            return readValue(type, codec, jsonReader);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private DocumentParser newParser(final Reader reader) {
        final var parser = parserFactory.apply(reader);
        if (!rejectDuplicateKeys && maxDepth == 0 && parser.getClass() == JsonParser.class) {
            // Reuse the core parser's structure stack; external parsers need event tracking.
            return ((JsonParser) parser).forDocument();
        }
        return new ValidatingParser(parser, rejectDuplicateKeys, maxDepth);
    }

    @Override
    public void close() {
        if (ignoreCodecClose) {
            return;
        }

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

    private boolean anyValueNotInstanceOf(final Map<?, ?> map, final Class<?> itemClass) {
        for (final var v : map.values()) {
            if (v != null && !itemClass.isInstance(v)) {
                return true;
            }
        }
        return false;
    }

    private ExtendedWriter wrap(final Writer writer) {
        return writer instanceof ExtendedWriter ew ? ew : new ExtendedWriter(writer);
    }

    // JSON payloads are mostly ASCII so inflate bytes directly, only the (rare) non ASCII tail
    // goes through the JDK UTF-8 decoder - way cheaper than an InputStreamReader/StreamDecoder chain
    private static char[] utf8ToChars(final byte[] bytes) {
        final int length = bytes.length;
        final var chars = new char[length];
        for (int i = 0; i < length; i++) {
            final byte b = bytes[i];
            if (b < 0) {
                final var suffix = new String(bytes, i, length - i, UTF_8);
                final int suffixLength = suffix.length();
                if (i + suffixLength == length) {
                    suffix.getChars(0, suffixLength, chars, i);
                    return chars;
                }
                final var exact = new char[i + suffixLength];
                System.arraycopy(chars, 0, exact, 0, i);
                suffix.getChars(0, suffixLength, exact, i);
                return exact;
            }
            chars[i] = (char) b;
        }
        return chars;
    }

    private static int maxStringLength(final Configuration configuration) {
        return configuration.get("fusion.json.maxStringLength")
                .map(Integer::parseInt)
                .orElse(8 * 1024);
    }

    private static int maxBuffers(final Configuration configuration) {
        return configuration.get("fusion.json.maxBuffers")
                .map(Integer::parseInt)
                .orElse(-1);
    }

    private static int maxBigDecimalScale(final Configuration configuration) {
        return configuration.get("fusion.json.maxBigDecimalScale")
                .map(Integer::parseInt)
                .orElse(JsonParser.DEFAULT_MAX_BIG_DECIMAL_SCALE);
    }

    private static BufferProvider createBufferProvider(final Configuration configuration) {
        return new BufferProvider(maxStringLength(configuration), maxBuffers(configuration));
    }

    private static Function<Reader, Parser> createReaderParserFunction(final Configuration configuration) {
        final int maxStringLength = maxStringLength(configuration);
        final boolean autoAdjust = configuration.get("fusion.json.bufferAutoAdjust")
                .map(Boolean::parseBoolean)
                .orElse(true);
        final var bufferFactory = createBufferProvider(configuration);
        final int maxBigDecimalScale = maxBigDecimalScale(configuration);
        return reader -> new JsonParser(reader, maxStringLength, bufferFactory, autoAdjust, maxBigDecimalScale);
    }

    private static class ConfiguringImpl implements Configuring {
        private final JsonMapperImpl parent;
        private boolean serializeNulls;
        private boolean rejectIncompatibleValues;
        private boolean rejectDuplicateKeys;
        private boolean rejectTrailingTokens;
        private int maxDepth;
        private boolean rejectUnknownFields;

        private ConfiguringImpl(final JsonMapperImpl jsonMapper) {
            this.parent = jsonMapper;
            this.serializeNulls = parent.serializeNulls;
            this.rejectIncompatibleValues = parent.rejectIncompatibleValues;
            this.rejectDuplicateKeys = parent.rejectDuplicateKeys;
            this.rejectTrailingTokens = parent.rejectTrailingTokens;
            this.maxDepth = parent.maxDepth;
            this.rejectUnknownFields = parent.rejectUnknownFields;
        }

        @Override
        public Configuring rejectIncompatibleValues(final boolean reject) {
            this.rejectIncompatibleValues = reject;
            return this;
        }

        @Override
        public Configuring rejectDuplicateKeys(final boolean reject) {
            this.rejectDuplicateKeys = reject;
            return this;
        }

        @Override
        public Configuring rejectTrailingTokens(final boolean reject) {
            this.rejectTrailingTokens = reject;
            return this;
        }

        @Override
        public Configuring rejectUnknownFields(final boolean reject) {
            this.rejectUnknownFields = reject;
            return this;
        }

        @Override
        public Configuring maxDepth(final int maxDepth) {
            if (maxDepth < 0) {
                throw new IllegalArgumentException("maxDepth must be non-negative");
            }
            this.maxDepth = maxDepth;
            return this;
        }

        @Override
        public Configuring serializeNulls() {
            this.serializeNulls = true;
            return this;
        }

        @Override
        public JsonMapper build() {
            return new JsonMapperImpl(parent.codecs, parent.parserFactory, serializeNulls, true,
                    rejectIncompatibleValues, rejectDuplicateKeys, rejectTrailingTokens, maxDepth, rejectUnknownFields);
        }
    }
}
