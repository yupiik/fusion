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

import io.yupiik.fusion.json.JsonException;
import io.yupiik.fusion.json.spi.Parser;

import java.math.BigDecimal;

/** Generic JSON numbers, with neither quoted-number coercion nor narrowing. */
public class GenericNumberJsonCodec extends NumberJsonCodec<Number> {
    public GenericNumberJsonCodec() {
        super(Number.class);
    }

    @Override
    public Number read(final DeserializationContext context) {
        final var parser = context.parser();
        if (!parser.hasNext()) {
            throw new JsonException("Expected a JSON number");
        }
        return switch (parser.next()) {
            case VALUE_NULL -> null;
            case VALUE_NUMBER -> parser.getBigDecimal();
            default -> throw new JsonException("Expected a JSON number or null");
        };
    }

    @Override
    protected Number read(final Parser parser) {
        return parser.getBigDecimal();
    }

    @Override
    protected Number mapBigDecimal(final BigDecimal value) {
        return value;
    }
}
