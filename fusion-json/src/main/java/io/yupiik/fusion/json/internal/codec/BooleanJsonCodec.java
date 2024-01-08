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

import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;

public class BooleanJsonCodec implements JsonCodec<Boolean> {
    @Override
    public Type type() {
        return Boolean.class;
    }

    @Override
    public Boolean read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext()) {
            throw new IllegalStateException("No value");
        }
        final var next = parser.next();
        return switch (next) {
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            default -> throw new IllegalStateException("Expected true/false and got " + next);
        };
    }

    @Override
    public void write(final Boolean value, final SerializationContext context) throws IOException {
        context.writer().write(String.valueOf(value));
    }
}
