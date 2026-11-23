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
package io.yupiik.fusion.json.serialization;

import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.internal.codec.BaseJsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * Shared runtime for generated, direct-call model bindings.
 * Delegates are resolved from each invocation's context, never retained by this codec.
 * The supplied bindings must be immutable and safe for concurrent use.
 *
 * @param <A> model type
 */
public final class MetadataJsonCodec<A> extends BaseJsonCodec<A> {
    private final char[][] keys;
    private final FieldMeta<A>[] readFields;
    private final FieldMeta<A>[] writeFields;
    private final IntUnaryOperator keyLengths;
    private final Function<Object[], A> factory;
    private final Set<String> ignoredNames;

    /**
     * @param properties all constructor properties, with named properties in key-length order
     *                   (others and ignored properties can occur anywhere and are moved after named properties)
     * @param writeOrder property IDs in serialization order, excluding ignored properties
     * @param accessors typed model accessors in constructor-slot order; ignored properties may use null
     * @param keyLengths first named read-key index for each length, or -1 if absent
     * @param factory constructor callback consuming constructor slots
     */
    @SuppressWarnings("unchecked")
    public MetadataJsonCodec(final Type type, final List<Property> properties, final int[] writeOrder,
                             final List<Function<A, Object>> accessors, final IntUnaryOperator keyLengths,
                             final Function<Object[], A> factory) {
        super(Objects.requireNonNull(type));
        this.keyLengths = Objects.requireNonNull(keyLengths);
        this.factory = Objects.requireNonNull(factory);
        if (accessors.size() != properties.size()) {
            throw new IllegalArgumentException("An accessor slot is required for every constructor property");
        }
        final var ordered = new ArrayList<Property>(properties.size());
        final var slots = new HashSet<Integer>();
        final var ids = new HashSet<Integer>();
        final var ignored = new HashSet<String>();
        int previousLength = -1;
        int others = 0;
        for (final var property : properties) {
            if (!slots.add(property.slotIndex()) || property.slotIndex() < 0 || property.slotIndex() >= properties.size()) {
                throw new IllegalArgumentException("Invalid or duplicate constructor slot: " + property.slotIndex());
            }
            if (!ids.add(property.id())) {
                throw new IllegalArgumentException("Duplicate property ID: " + property.id());
            }
            if (property.ignored()) {
                ignored.add(Objects.requireNonNull(property.jsonName()));
            }
            if (property.others()) {
                if (property.ignored() || ++others > 1) {
                    throw new IllegalArgumentException("Only one non-ignored others property is supported");
                }
            } else if (!property.ignored()) {
                final int length = Objects.requireNonNull(property.jsonName()).length();
                if (length < previousLength) {
                    throw new IllegalArgumentException("Named properties must be ordered by JSON key length");
                }
                previousLength = length;
                ordered.add(property);
            }
        }
        keys = new char[ordered.size()][];
        ignoredNames = Set.copyOf(ignored);
        for (int i = 0; i < keys.length; i++) {
            keys[i] = ordered.get(i).jsonName().toCharArray();
        }
        for (final var property : properties) {
            if (property.others() || property.ignored()) {
                ordered.add(property);
            }
        }
        readFields = (FieldMeta<A>[]) new FieldMeta<?>[ordered.size()];
        final var byId = new HashMap<Integer, FieldMeta<A>>();
        for (int i = 0; i < ordered.size(); i++) {
            final var property = ordered.get(i);
            final var field = new FieldMeta<A>(
                    property.jsonName() == null ? null : property.jsonName().toCharArray(), property.slotIndex(),
                    BaseJsonCodec.ContainerKind.valueOf(property.container().name()),
                    BaseJsonCodec.ValueKind.valueOf(property.valueKind().name()), property.wrapper(), property.others(),
                    property.delegateType(), property.ignored() ? null : Objects.requireNonNull(accessors.get(property.slotIndex())), 0,
                    property.others() || property.ignored() ? null : (JsonStrings.escape(property.jsonName()) + ':').toCharArray());
            readFields[i] = field;
            if (!property.ignored()) {
                byId.put(property.id(), field);
            }
        }
        writeFields = (FieldMeta<A>[]) new FieldMeta<?>[writeOrder.length];
        for (int i = 0; i < writeOrder.length; i++) {
            final var field = byId.remove(writeOrder[i]);
            if (field == null) {
                throw new IllegalArgumentException("Unknown, ignored or duplicate write property ID: " + writeOrder[i]);
            }
            writeFields[i] = field;
        }
        if (!byId.isEmpty()) {
            throw new IllegalArgumentException("Write order must contain every non-ignored property");
        }
    }

    @Override
    public A read(final DeserializationContext context) throws IOException {
        return readObject(context, keys, keyLengths, readFields, factory, ignoredNames);
    }

    @Override
    public void write(final A value, final SerializationContext context) throws IOException {
        writeObject(value, context, writeFields);
    }

    public enum ContainerKind {
        VALUE, LIST, SET, MAP, MAP_LIST
    }

    public enum ValueKind {
        BOOLEAN, BIG_DECIMAL, NUMBER, INTEGER, LONG, DOUBLE, STRING, ENUM,
        LOCAL_DATE, LOCAL_DATE_TIME, OFFSET_DATE_TIME, ZONED_DATE_TIME,
        GENERIC_OBJECT, MODEL
    }

    /**
     * Immutable metadata: names are unescaped JSON names; slots cover 0 through property count minus one.
     * IDs are metadata-local property IDs, independent of read order, write order and constructor slots.
     * Ignored properties still participate in constructor default initialization, but never access the model.
     */
    public record Property(int id, String jsonName, int slotIndex, ContainerKind container, ValueKind valueKind,
                           boolean wrapper, boolean others, boolean ignored, Class<?> delegateType) {
    }

}
