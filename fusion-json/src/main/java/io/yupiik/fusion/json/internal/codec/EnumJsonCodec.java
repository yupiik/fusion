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
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.CharBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_STRING;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

public class EnumJsonCodec<A extends Enum<A>> implements JsonCodec<A> {
    private final Class<A> type;
    private final Map<A, char[]> toJson;
    private final Map<CharBuffer, A> fromJson;

    public EnumJsonCodec(final Class<A> type, final Collection<A> enumValues, final Function<A, String> toJson) {
        this.type = type;
        // must be bijective - but anyway it is needed in practise, or you loose info!
        this.toJson = enumValues.stream().collect(toMap(identity(), it -> {
            final var chars = JsonStrings.escapeChars(toJson.apply(it));
            if (chars.length() == chars.capacity()) {
                return chars.array();
            }
            final var array = new char[chars.length()];
            chars.get(array, chars.position(), chars.limit());
            return array;
        }));
        this.fromJson = enumValues.stream().collect(toMap(e -> CharBuffer.wrap(toJson.apply(e).toCharArray()), identity()));
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public A read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext() || parser.next() != VALUE_STRING) {
            throw new IllegalStateException("Expected VALUE_STRING");
        }
        final var string = parser.getChars();
        final var value = fromJson.get(string);
        if (value == null) {
            throw new IllegalArgumentException("Unknown enum '" + string + "'");
        }
        return value;
    }

    @Override
    public void write(final A value, final SerializationContext context) throws IOException {
        final var cbuf = toJson.get(value);
        if (cbuf == null) {
            context.writer().write("null"); // todo: fail?
        } else {
            context.writer().write(cbuf);
        }
    }
}
