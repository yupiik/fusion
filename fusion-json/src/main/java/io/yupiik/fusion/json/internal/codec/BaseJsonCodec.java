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
package io.yupiik.fusion.json.internal.codec;

import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.serialization.ExtendedWriter;
import io.yupiik.fusion.json.serialization.JsonCodec;
import io.yupiik.fusion.json.spi.Parser;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// hosts the utilities shared by the generated codecs to keep the generated sources small,
// the helper behaviors (attribute ordering, null handling) are part of the generation contract
// so they must stay stable
public abstract class BaseJsonCodec<A> implements JsonCodec<A> {
    protected static final char[] NULL = "null".toCharArray();
    protected static final char[] TRUE = "true".toCharArray();
    protected static final char[] FALSE = "false".toCharArray();
    private static final char[] LONG_MIN_VALUE = String.valueOf(Long.MIN_VALUE).toCharArray();

    protected final Type type;

    protected BaseJsonCodec(final Type type) {
        this.type = type;
    }

    @Override
    public Type type() {
        return type;
    }

    protected void writeJsonOthers(final Map<String, Object> others, final SerializationContext context) throws IOException {
        final var delegate = context.codec(Object.class);
        final var writer = context.writer();
        final var it = others.entrySet().iterator();
        while (it.hasNext()) {
            final var entry = it.next();
            if (entry.getValue() == null) {
                continue;
            }
            JsonStrings.escapeCharsTo(entry.getKey(), writer);
            writer.write(':');
            delegate.write(entry.getValue(), context);
            if (it.hasNext()) {
                writer.write(',');
            }
        }
    }

    // all the writeXxx(boolean, char[], ...) helpers take the "is it the first attribute" flag and
    // return its new value: false when something was written, unchanged when the attribute was skipped

    protected boolean writeJsonOthers(final boolean firstAttribute, final char[] name, final Map<String, Object> value,
                                      final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        // note: the name is not written, the attributes of the map are flattened in the enclosing object
        final var first = separator(firstAttribute, context);
        writeJsonOthers(value, context);
        return first;
    }

    protected boolean separator(final boolean firstAttribute, final SerializationContext context) throws IOException {
        if (!firstAttribute) {
            context.writer().write(',');
        }
        return false;
    }

    protected boolean writeNullAttribute(final boolean firstAttribute, final char[] name,
                                         final SerializationContext context) throws IOException {
        if (!context.needsNull()) {
            return firstAttribute;
        }
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write(NULL);
        return first;
    }

    protected boolean writeValue(final boolean firstAttribute, final char[] name, final String rawJson,
                                 final SerializationContext context) throws IOException {
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write(rawJson);
        return first;
    }

    protected boolean writeValue(final boolean firstAttribute, final char[] name, final int value,
                                 final SerializationContext context) throws IOException {
        return writeValue(firstAttribute, name, (long) value, context);
    }

    protected boolean writeValue(final boolean firstAttribute, final char[] name, final long value,
                                 final SerializationContext context) throws IOException {
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writeLong(writer, value);
        return first;
    }

    protected boolean writeValue(final boolean firstAttribute, final char[] name, final boolean value,
                                 final SerializationContext context) throws IOException {
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write(value ? TRUE : FALSE);
        return first;
    }

    protected boolean writeValue(final boolean firstAttribute, final char[] name, final double value,
                                 final SerializationContext context) throws IOException {
        return writeValue(firstAttribute, name, String.valueOf(value), context);
    }

    protected boolean writeNullable(final boolean firstAttribute, final char[] name, final Object value,
                                    final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        if (value instanceof Integer i) {
            return writeValue(firstAttribute, name, i.longValue(), context);
        }
        if (value instanceof Long l) {
            return writeValue(firstAttribute, name, l.longValue(), context);
        }
        if (value instanceof Boolean b) {
            return writeValue(firstAttribute, name, b.booleanValue(), context);
        }
        return writeValue(firstAttribute, name, String.valueOf(value), context);
    }

    protected boolean writeString(final boolean firstAttribute, final char[] name, final CharSequence value,
                                  final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        JsonStrings.escapeCharsTo(value, writer);
        return first;
    }

    // digits are written from a small stack buffer to avoid a String allocation per numeric attribute
    private static void writeLong(final ExtendedWriter writer, final long value) throws IOException {
        if (value == Long.MIN_VALUE) { // can't be negated
            writer.write(LONG_MIN_VALUE);
            return;
        }
        final var buffer = new char[20];
        int idx = 20;
        final boolean negative = value < 0;
        long remaining = negative ? -value : value;
        do {
            buffer[--idx] = (char) ('0' + (remaining % 10));
            remaining /= 10;
        } while (remaining != 0);
        if (negative) {
            buffer[--idx] = '-';
        }
        writer.write(buffer, idx, 20 - idx);
    }

    protected <T> boolean writeWithCodec(final boolean firstAttribute, final char[] name, final T value,
                                         final Class<T> type, final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var codec = context.codec(type);
        final var first = separator(firstAttribute, context);
        context.writer().write(name);
        codec.write(value, context);
        return first;
    }

    protected boolean writeRawCollection(final boolean firstAttribute, final char[] name, final Collection<?> value,
                                         final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write('[');
        final var it = value.iterator();
        while (it.hasNext()) {
            writer.write(String.valueOf(it.next()));
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write(']');
        return first;
    }

    protected boolean writeStringCollection(final boolean firstAttribute, final char[] name,
                                            final Collection<? extends CharSequence> value,
                                            final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write('[');
        final var it = value.iterator();
        while (it.hasNext()) {
            final var next = it.next();
            if (next == null) {
                writer.write(NULL);
            } else {
                JsonStrings.escapeCharsTo(next, writer);
            }
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write(']');
        return first;
    }

    protected <T> boolean writeCollection(final boolean firstAttribute, final char[] name,
                                          final Collection<? extends T> value, final Class<T> itemType,
                                          final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var codec = context.codec(itemType);
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write('[');
        final var it = value.iterator();
        while (it.hasNext()) {
            final var next = it.next();
            if (next == null) {
                writer.write(NULL);
            } else {
                codec.write(next, context);
            }
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write(']');
        return first;
    }

    protected boolean writeRawMap(final boolean firstAttribute, final char[] name, final Map<String, ?> value,
                                  final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write('{');
        final var it = value.entrySet().iterator();
        while (it.hasNext()) {
            final var next = it.next();
            JsonStrings.escapeCharsTo(next.getKey(), writer);
            writer.write(':');
            writer.write(String.valueOf(next.getValue()));
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write('}');
        return first;
    }

    protected boolean writeStringMap(final boolean firstAttribute, final char[] name,
                                     final Map<String, ? extends CharSequence> value,
                                     final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write('{');
        final var it = value.entrySet().iterator();
        while (it.hasNext()) {
            final var next = it.next();
            JsonStrings.escapeCharsTo(next.getKey(), writer);
            writer.write(':');
            if (next.getValue() == null) {
                writer.write(NULL);
            } else {
                JsonStrings.escapeCharsTo(next.getValue(), writer);
            }
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write('}');
        return first;
    }

    protected <T> boolean writeMapWithCodec(final boolean firstAttribute, final char[] name,
                                            final Map<String, ? extends T> value, final Class<T> valueType,
                                            final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var codec = context.codec(valueType);
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write('{');
        final var it = value.entrySet().iterator();
        while (it.hasNext()) {
            final var next = it.next();
            JsonStrings.escapeCharsTo(next.getKey(), writer);
            writer.write(':');
            if (next.getValue() == null) {
                writer.write(NULL);
            } else {
                codec.write(next.getValue(), context);
            }
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write('}');
        return first;
    }

    protected boolean writeRawMapList(final boolean firstAttribute, final char[] name,
                                      final Map<String, ? extends Collection<?>> value,
                                      final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write('{');
        final var it = value.entrySet().iterator();
        while (it.hasNext()) {
            final var next = it.next();
            final var rawNextValue = next.getValue();
            if (rawNextValue == null) { // unlikely but possible
                continue;
            }
            JsonStrings.escapeCharsTo(next.getKey(), writer);
            writer.write(":[");
            final var nextValue = rawNextValue.iterator();
            while (nextValue.hasNext()) {
                writer.write(String.valueOf(nextValue.next()));
                if (nextValue.hasNext()) {
                    writer.write(',');
                }
            }
            writer.write(']');
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write('}');
        return first;
    }

    protected boolean writeStringMapList(final boolean firstAttribute, final char[] name,
                                         final Map<String, ? extends Collection<? extends CharSequence>> value,
                                         final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write('{');
        final var it = value.entrySet().iterator();
        while (it.hasNext()) {
            final var next = it.next();
            final var rawNextValue = next.getValue();
            if (rawNextValue == null) { // unlikely but possible
                continue;
            }
            JsonStrings.escapeCharsTo(next.getKey(), writer);
            writer.write(":[");
            final var nextValue = rawNextValue.iterator();
            while (nextValue.hasNext()) {
                final var item = nextValue.next();
                if (item == null) {
                    writer.write(NULL);
                } else {
                    JsonStrings.escapeCharsTo(item, writer);
                }
                if (nextValue.hasNext()) {
                    writer.write(',');
                }
            }
            writer.write(']');
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write('}');
        return first;
    }

    protected <T> boolean writeMapListWithCodec(final boolean firstAttribute, final char[] name,
                                                final Map<String, ? extends Collection<? extends T>> value,
                                                final Class<T> itemType,
                                                final SerializationContext context) throws IOException {
        if (value == null) {
            return writeNullAttribute(firstAttribute, name, context);
        }
        final var itemCodec = context.codec(itemType);
        final var first = separator(firstAttribute, context);
        final var writer = context.writer();
        writer.write(name);
        writer.write('{');
        final var it = value.entrySet().iterator();
        while (it.hasNext()) {
            final var next = it.next();
            final var rawNextValue = next.getValue();
            if (rawNextValue == null) { // unlikely but possible
                continue;
            }
            JsonStrings.escapeCharsTo(next.getKey(), writer);
            writer.write(":[");
            final var nextValue = rawNextValue.iterator();
            while (nextValue.hasNext()) {
                final var item = nextValue.next();
                if (item == null) {
                    writer.write(NULL);
                } else {
                    itemCodec.write(item, context);
                }
                if (nextValue.hasNext()) {
                    writer.write(',');
                }
            }
            writer.write(']');
            if (it.hasNext()) {
                writer.write(',');
            }
        }
        writer.write('}');
        return first;
    }

    protected <T> List<T> readList(final DeserializationContext context, final Class<T> itemType) throws IOException {
        return readCollection(context, new ArrayList<>(), context.codec(itemType));
    }

    protected <T> Set<T> readSet(final DeserializationContext context, final Class<T> itemType) throws IOException {
        return readCollection(context, new HashSet<>(), context.codec(itemType));
    }

    protected <T> Map<String, T> readMap(final DeserializationContext context, final Class<T> valueType) throws IOException {
        final var delegate = context.codec(valueType);
        final var reader = context.parser();
        reader.enforceNext(Parser.Event.START_OBJECT);

        final var instance = new LinkedHashMap<String, T>();
        Parser.Event event;
        while (reader.hasNext() && (event = reader.next()) != Parser.Event.END_OBJECT) {
            reader.rewind(event);

            final var keyEvent = reader.next();
            if (keyEvent != Parser.Event.KEY_NAME) {
                throw new IllegalStateException("Expected=KEY_NAME, but got " + keyEvent);
            }
            instance.put(reader.getString(), delegate.read(context));
        }
        return instance;
    }

    protected <T> Map<String, List<T>> readMapList(final DeserializationContext context, final Class<T> itemType) throws IOException {
        final var delegate = context.codec(itemType);
        final var reader = context.parser();
        reader.enforceNext(Parser.Event.START_OBJECT);

        final var instance = new LinkedHashMap<String, List<T>>();
        Parser.Event event;
        while (reader.hasNext() && (event = reader.next()) != Parser.Event.END_OBJECT) {
            reader.rewind(event);

            final var keyEvent = reader.next();
            if (keyEvent != Parser.Event.KEY_NAME) {
                throw new IllegalStateException("Expected=KEY_NAME, but got " + keyEvent);
            }
            instance.put(reader.getString(), readCollection(context, new ArrayList<>(), delegate));
        }
        return instance;
    }

    private <T, C extends Collection<T>> C readCollection(final DeserializationContext context, final C instance,
                                                           final JsonCodec<T> delegate) throws IOException {
        final var reader = context.parser();
        if (!reader.hasNext()) {
            throw new IllegalStateException("No more element");
        }

        final var next = reader.next();
        if (next != Parser.Event.START_ARRAY) {
            throw new IllegalStateException("Expected=START_ARRAY, but got " + next);
        }

        Parser.Event event;
        while (reader.hasNext() && (event = reader.next()) != Parser.Event.END_ARRAY) {
            reader.rewind(event);
            instance.add(delegate.read(context));
        }
        return instance;
    }

    // -----------------------------------------------------------------------
    // Table-driven codec engine (Step 1 of the optimizer plan)
    // Generated codecs emit a FieldMeta table + factory lambda; the control
    // flow lives here in BaseJsonCodec instead of in generated source.
    // -----------------------------------------------------------------------

    public enum ContainerKind {
        VALUE, LIST, SET, MAP, MAP_LIST
    }

    public enum ValueKind {
        BOOLEAN, BIG_DECIMAL, INTEGER, LONG, DOUBLE, STRING, ENUM,
        LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME,
        GENERIC_OBJECT, MODEL
    }

    public record FieldMeta<A>(
            char[] jsonName,
            int slotIndex,
            ContainerKind container,
            ValueKind valueKind,
            boolean isWrapper,
            boolean isOthers,
            Class<?> delegateType,
            Function<A, Object> accessor,
            int order,
            char[] keyPrefix
    ) {
    }

    protected <A> A readObject(final DeserializationContext context, final char[][] keys,
                               final FieldMeta<A>[] fields, final Function<Object[], A> factory) throws IOException {
        final var parser = context.parser();
        parser.enforceNext(Parser.Event.START_OBJECT);

        final var slots = new Object[fields.length];
        int othersSlot = -1;
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].isOthers()) {
                othersSlot = i;
            } else if (fields[i].container() == ContainerKind.VALUE) {
                if (fields[i].isWrapper()) {
                    slots[i] = null;
                } else {
                    slots[i] = switch (fields[i].valueKind()) {
                        case INTEGER -> 0;
                        case LONG -> 0L;
                        case BOOLEAN -> Boolean.FALSE;
                        case DOUBLE -> 0.0d;
                        default -> null;
                    };
                }
            }
        }
        final var others = othersSlot >= 0 ? new LinkedHashMap<String, Object>() : null;

        int key = -1;
        String fallbackKey = null;

        Parser.Event event;
        while (parser.hasNext()) {
            event = parser.next();
            switch (event) {
                case KEY_NAME -> {
                    key = parser.matchString(keys);
                    if (othersSlot >= 0 && key < 0) {
                        fallbackKey = parser.getString();
                    }
                }
                case VALUE_STRING -> {
                    if (key >= 0) {
                        final var value = readStringValue(context, parser, event, fields[key]);
                        if (value != null) {
                            slots[fields[key].slotIndex()] = value;
                        }
                    } else if (others != null) {
                        others.put(fallbackKey, parser.getString());
                    }
                    key = -1;
                }
                case VALUE_NUMBER -> {
                    if (key >= 0) {
                        final var value = readNumberValue(context, parser, event, fields[key]);
                        if (value != null) {
                            slots[fields[key].slotIndex()] = value;
                        }
                    } else if (others != null) {
                        others.put(fallbackKey, parser.getBigDecimal());
                    }
                    key = -1;
                }
                case VALUE_TRUE, VALUE_FALSE -> {
                    if (key >= 0) {
                        final var field = fields[key];
                        if (field.valueKind() == ValueKind.BOOLEAN || field.valueKind() == ValueKind.GENERIC_OBJECT) {
                            slots[field.slotIndex()] = event == Parser.Event.VALUE_TRUE;
                        }
                    } else if (others != null) {
                        others.put(fallbackKey, event == Parser.Event.VALUE_TRUE);
                    }
                    key = -1;
                }
                case START_OBJECT -> {
                    if (key >= 0) {
                        final var field = fields[key];
                        parser.rewind(event);
                        switch (field.container()) {
                            case VALUE -> {
                                if (field.valueKind() == ValueKind.GENERIC_OBJECT) {
                                    slots[field.slotIndex()] = context.codec(Object.class).read(context);
                                } else {
                                    slots[field.slotIndex()] = context.codec(field.delegateType()).read(context);
                                }
                            }
                            case MAP -> {
                                if (field.valueKind() == ValueKind.GENERIC_OBJECT) {
                                    slots[field.slotIndex()] = context.codec(Object.class).read(context);
                                } else {
                                    slots[field.slotIndex()] = readMap(context, field.delegateType());
                                }
                            }
                            case MAP_LIST -> {
                                slots[field.slotIndex()] = readMapList(context, field.delegateType());
                            }
                            default -> parser.skipObject();
                        }
                    } else if (others != null) {
                        parser.rewind(event);
                        others.put(fallbackKey, context.codec(Object.class).read(context));
                    } else {
                        parser.skipObject();
                    }
                    key = -1;
                }
                case START_ARRAY -> {
                    if (key >= 0) {
                        final var field = fields[key];
                        parser.rewind(event);
                        switch (field.container()) {
                            case LIST -> slots[field.slotIndex()] = readList(context, field.delegateType());
                            case SET -> slots[field.slotIndex()] = readSet(context, field.delegateType());
                            case VALUE -> {
                                if (field.valueKind() == ValueKind.GENERIC_OBJECT) {
                                    slots[field.slotIndex()] = readList(context, Object.class);
                                } else {
                                    parser.skipArray();
                                }
                            }
                            default -> parser.skipArray();
                        }
                    } else if (others != null) {
                        parser.rewind(event);
                        others.put(fallbackKey, context.codec(Object.class).read(context));
                    } else {
                        parser.skipArray();
                    }
                    key = -1;
                }
                case VALUE_NULL -> {
                    if (key >= 0) {
                        slots[fields[key].slotIndex()] = null;
                    } else if (others != null) {
                        others.put(fallbackKey, null);
                    }
                    key = -1;
                }
                case END_OBJECT -> {
                    if (othersSlot >= 0) {
                        slots[othersSlot] = others;
                    }
                    return factory.apply(slots);
                }
                default -> key = -1;
            }
        }
        throw new IllegalArgumentException("Object didn't end (missing END_OBJECT)");
    }

    private static Object readStringValue(final DeserializationContext context, final Parser parser,
                                          final Parser.Event event, final FieldMeta<?> field) throws IOException {
        return switch (field.valueKind()) {
            case STRING, GENERIC_OBJECT -> parser.getString();
            case ENUM, BIG_DECIMAL, LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME -> {
                parser.rewind(event);
                yield context.codec(field.delegateType()).read(context);
            }
            default -> null;
        };
    }

    private static Object readNumberValue(final DeserializationContext context, final Parser parser,
                                          final Parser.Event event, final FieldMeta<?> field) throws IOException {
        return switch (field.valueKind()) {
            case INTEGER -> parser.getInt();
            case LONG -> parser.getLong();
            case DOUBLE -> parser.getDouble();
            case BIG_DECIMAL -> {
                parser.rewind(event);
                yield context.codec(BigDecimal.class).read(context);
            }
            case GENERIC_OBJECT -> parser.getBigDecimal();
            default -> null;
        };
    }

    protected <A> void writeObject(final A instance, final SerializationContext context,
                                   final FieldMeta<A>[] fields) throws IOException {
        final var writer = context.writer();
        writer.write('{');
        boolean first = true;
        for (final var field : fields) {
            first = writeField(first, instance, field, context);
        }
        writer.write('}');
    }

    @SuppressWarnings("unchecked")
    private <A> boolean writeField(boolean first, final A instance, final FieldMeta<A> field,
                                   final SerializationContext context) throws IOException {
        if (field.isOthers()) {
            final var val = field.accessor().apply(instance);
            return writeJsonOthers(first, field.keyPrefix(), (Map<String, Object>) val, context);
        }
        final var value = field.accessor().apply(instance);
        if (value == null) {
            return writeNullAttribute(first, field.keyPrefix(), context);
        }
        return switch (field.container()) {
            case VALUE -> switch (field.valueKind()) {
                case INTEGER -> writeValue(first, field.keyPrefix(), ((Number) value).intValue(), context);
                case LONG -> writeValue(first, field.keyPrefix(), ((Number) value).longValue(), context);
                case DOUBLE -> writeValue(first, field.keyPrefix(), ((Number) value).doubleValue(), context);
                case BOOLEAN -> writeValue(first, field.keyPrefix(), (Boolean) value, context);
                case STRING -> writeString(first, field.keyPrefix(), (CharSequence) value, context);
                case ENUM, BIG_DECIMAL, LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME, MODEL, GENERIC_OBJECT ->
                        writeWithCodec(first, field.keyPrefix(), value, (Class<Object>) field.delegateType(), context);
            };
            case LIST, SET -> {
                if (field.valueKind() == ValueKind.STRING) {
                    yield writeStringCollection(first, field.keyPrefix(), (Collection<? extends CharSequence>) value, context);
                }
                if (field.valueKind() == ValueKind.INTEGER || field.valueKind() == ValueKind.LONG ||
                        field.valueKind() == ValueKind.DOUBLE || field.valueKind() == ValueKind.BOOLEAN) {
                    yield writeRawCollection(first, field.keyPrefix(), (Collection<?>) value, context);
                }
                yield writeCollection(first, field.keyPrefix(), (Collection<?>) value,
                        (Class<Object>) field.delegateType(), context);
            }
            case MAP -> {
                if (field.valueKind() == ValueKind.STRING) {
                    yield writeStringMap(first, field.keyPrefix(), (Map<String, ? extends CharSequence>) value, context);
                }
                if (field.valueKind() == ValueKind.INTEGER || field.valueKind() == ValueKind.LONG ||
                        field.valueKind() == ValueKind.DOUBLE || field.valueKind() == ValueKind.BOOLEAN) {
                    yield writeRawMap(first, field.keyPrefix(), (Map<String, ?>) value, context);
                }
                yield writeMapWithCodec(first, field.keyPrefix(), (Map<String, ?>) value,
                        (Class<Object>) field.delegateType(), context);
            }
            case MAP_LIST -> {
                if (field.valueKind() == ValueKind.STRING) {
                    yield writeStringMapList(first, field.keyPrefix(),
                            (Map<String, ? extends Collection<? extends CharSequence>>) value, context);
                }
                if (field.valueKind() == ValueKind.INTEGER || field.valueKind() == ValueKind.LONG ||
                        field.valueKind() == ValueKind.DOUBLE || field.valueKind() == ValueKind.BOOLEAN) {
                    yield writeRawMapList(first, field.keyPrefix(), (Map<String, ? extends Collection<?>>) value, context);
                }
                yield writeMapListWithCodec(first, field.keyPrefix(),
                        (Map<String, ? extends Collection<?>>) value, (Class<Object>) field.delegateType(), context);
            }
        };
    }
}
