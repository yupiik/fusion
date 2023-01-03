/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
import java.math.BigDecimal;

public class BigDecimalJsonCodec implements JsonCodec<BigDecimal> {
    @Override
    public Type type() {
        return BigDecimal.class;
    }

    @Override
    public BigDecimal read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext()) {
            throw new IllegalStateException("No more token to process.");
        }
        final var event = parser.next();
        return switch (event) {
            case VALUE_NUMBER -> parser.getBigDecimal();
            case VALUE_STRING -> new BigDecimal(parser.getString());
            default -> throw new IllegalStateException("Expected VALUE_STRING or VALUE_NUMBER, got " + event);
        };
    }

    @Override
    public void write(final BigDecimal value, final SerializationContext context) throws IOException {
        context.writer().write(JsonStrings.escape(value.toString()));
    }
}
