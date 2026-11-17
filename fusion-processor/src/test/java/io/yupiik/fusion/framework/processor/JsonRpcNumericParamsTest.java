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
package io.yupiik.fusion.framework.processor;

import io.yupiik.fusion.framework.processor.test.Compiler;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jsonrpc.JsonRpcRegistry;
import io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonRpcNumericParamsTest {
    @Test
    @SuppressWarnings("unchecked")
    void genericNumbersAreConvertedWithoutAQuotedDecimalRoundTrip(@TempDir final Path work) throws IOException {
        new Compiler(work, "JsonRpcNumericParams").compileAndAssertsInstance((container, instance) -> {
            final var values = (Supplier<List<?>>) instance.instance();
            try (final var registry = container.lookup(JsonRpcRegistry.class);
                 final var mapper = container.lookup(JsonMapper.class)) {
                invoke(registry.instance(), mapper.instance(), "roots",
                        "{\"d\":1.25,\"boxedD\":2.5,\"f\":3.5,\"boxedF\":4.25,\"n\":5.125}");
                assertEquals(List.of(1.25d, 2.5d, 3.5f, 4.25f, new BigDecimal("5.125")), values.get());
                invoke(registry.instance(), mapper.instance(), "roots",
                        "{\"d\":null,\"boxedD\":null,\"f\":null,\"boxedF\":null,\"n\":null}");
                assertEquals(Arrays.asList(0d, null, 0f, null, null), values.get());

                invoke(registry.instance(), mapper.instance(), "collections",
                        "{\"d\":[1.25,null],\"f\":[2.5,null],\"n\":[3.75,null],\"set\":[4.5],\"collection\":[5.25]}");
                assertEquals(List.of(Arrays.asList(1.25d, null), Arrays.asList(2.5f, null),
                        Arrays.asList(new BigDecimal("3.75"), null), Set.of(4.5d), List.of(5.25f)), values.get());
                invoke(registry.instance(), mapper.instance(), "maps",
                        "{\"d\":{\"x\":1.25},\"f\":{\"x\":2.5},\"n\":{\"x\":3.75}}");
                assertEquals(List.of(Map.of("x", 1.25d), Map.of("x", 2.5f), Map.of("x", new BigDecimal("3.75"))), values.get());
                invoke(registry.instance(), mapper.instance(), "optionals", "{\"d\":1.25,\"f\":2.5,\"n\":3.75}");
                assertEquals(List.of(Optional.of(1.25d), Optional.of(2.5f), Optional.of(new BigDecimal("3.75"))), values.get());
                invoke(registry.instance(), mapper.instance(), "optionals", "{}");
                assertEquals(List.of(Optional.empty(), Optional.empty(), Optional.empty()), values.get());
            }
        });
    }

    private void invoke(final JsonRpcRegistry registry, final JsonMapper mapper, final String method, final String json) {
        registry.methods().get(method).invoke(new JsonRpcMethod.Context(null, mapper.fromString(Object.class, json)))
                .toCompletableFuture().join();
    }
}
