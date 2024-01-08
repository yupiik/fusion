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
package io.yupiik.fusion.framework.handlebars.compiler.part;

import io.yupiik.fusion.framework.handlebars.compiler.accessor.ChainedAccessor;
import io.yupiik.fusion.framework.handlebars.compiler.accessor.DataAwareAccessor;
import io.yupiik.fusion.framework.handlebars.compiler.accessor.IterableDataVariablesAccessor;
import io.yupiik.fusion.framework.handlebars.compiler.accessor.MapEntryDataVariablesAccessor;
import io.yupiik.fusion.framework.handlebars.spi.Accessor;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import static java.util.function.Function.identity;

public record EachVariablePart(String name, Function<Accessor, Part> itemPartFactory,
                               Accessor accessor, Accessor itemDefaultAccessor) implements Part {
    @Override
    public String apply(final RenderContext context, final Object currentData) {
        final var value = ".".equals(name) || "this".equals(name) ? currentData : accessor.find(currentData, name);
        if (value == null) {
            return "";
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return "";
            }
            return doApply(context, collection, identity(), currentData);
        }
        if (value instanceof Map<?, ?> nestedMap) {
            if (nestedMap.isEmpty()) {
                return "";
            }
            return doApply(context, nestedMap.entrySet(), MapEntryDataVariablesAccessor::new, currentData);
        }
        throw new IllegalArgumentException("Unsupported each for " + value);
    }

    private String doApply(final RenderContext context, final Collection<?> collection,
                           final Function<Accessor, Accessor> partsAccessor,
                           final Object root) {
        final var iterator = collection.iterator();
        final var iterableDataVariablesAccessor = new IterableDataVariablesAccessor(iterator, itemDefaultAccessor);
        final var dataVariableAccessor = new ChainedAccessor(
                iterableDataVariablesAccessor,
                new DataAwareAccessor(root, itemDefaultAccessor));
        final var item = itemPartFactory.apply(partsAccessor.apply(dataVariableAccessor));
        final var out = new StringBuilder();
        while (iterableDataVariablesAccessor.hasNext()) {
            final var data = item.apply(context, iterableDataVariablesAccessor.next());
            iterableDataVariablesAccessor.onNext();
            if (!data.isBlank()) {
                out.append(data);
                break;
            }
        }
        while (iterableDataVariablesAccessor.hasNext()) {
            final var output = item.apply(context, iterableDataVariablesAccessor.next());
            if (!output.isBlank()) {
                out.append('\n').append(output);
            }
            iterableDataVariablesAccessor.onNext();
        }
        return out.toString();
    }
}
