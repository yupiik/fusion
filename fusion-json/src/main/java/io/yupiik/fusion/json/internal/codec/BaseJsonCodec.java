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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
}
