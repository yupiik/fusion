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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.function.Function.identity;

/**
 * {@code {{#each source limit=.. offset=..}}} iterates a collection or a map rendering the block per item,
 * with {@code @first}/{@code @last}/{@code @index} (and {@code @key}/{@code @value} for maps) data variables.
 * `limit` and `offset` slice the iteration, the {@code {{else}}} branch renders when there is nothing to
 * iterate (null, empty or fully consumed), and `as |value key|` binds block params (value = the item or
 * the entry value for maps, key = the index or the entry key). `../` inside the block refers to the
 * enclosing data.
 */
public record EachVariablePart(ArgEvaluator source,
                               Function<Accessor, Part> itemPartFactory,
                               Accessor accessor,
                               Accessor itemDefaultAccessor,
                               ArgEvaluator limit,
                               ArgEvaluator offset,
                               Part elsePart,
                               List<String> blockParams) implements Part {
    @Override
    public String apply(final RenderContext context, final Object currentData) {
        final var value = source.eval(accessor, currentData, context);
        if (value == null) {
            return elsePart == null ? "" : elsePart.apply(context, currentData);
        }
        if (value instanceof Collection<?> collection) {
            return doApply(context, collection, identity(), currentData);
        }
        if (value instanceof Map<?, ?> nestedMap) {
            return doApply(context, nestedMap.entrySet(),
                    a -> new MapEntryDataVariablesAccessor(a, itemDefaultAccessor), currentData);
        }
        throw new IllegalArgumentException("Unsupported each for " + value);
    }

    private String doApply(final RenderContext context, final Collection<?> collection,
                           final Function<Accessor, Accessor> partsAccessor,
                           final Object root) {
        final var iterator = collection.iterator();
        final var skip = offset == null ? 0 : asInt(offset.eval(accessor, root, context), "offset");
        final var cap = limit == null ? Integer.MAX_VALUE : asInt(limit.eval(accessor, root, context), "limit");
        if (skip < 0) {
            throw new IllegalArgumentException("offset must be a positive number but was " + skip);
        }
        if (cap < 0) {
            throw new IllegalArgumentException("limit must be a positive number but was " + cap);
        }

        final var iterableDataVariablesAccessor = new IterableDataVariablesAccessor(iterator, itemDefaultAccessor);
        var skipped = 0;
        while (iterableDataVariablesAccessor.hasNext() && skipped < skip) {
            iterableDataVariablesAccessor.next();
            iterableDataVariablesAccessor.onNext();
            skipped++;
        }
        if (cap == 0 || !iterableDataVariablesAccessor.hasNext()) {
            return elsePart == null ? "" : elsePart.apply(context, root);
        }

        final var dataVariableAccessor = new ChainedAccessor(
                iterableDataVariablesAccessor,
                new DataAwareAccessor(root, itemDefaultAccessor));
        final var itemAccessor = partsAccessor.apply(dataVariableAccessor);
        final var item = itemPartFactory.apply(itemAccessor);
        final var out = new StringBuilder();
        var rendered = 0;
        Object first = null;
        while (first == null && iterableDataVariablesAccessor.hasNext() && rendered < cap) {
            final var data = renderItem(context, itemAccessor, item, iterableDataVariablesAccessor, skip, rendered);
            rendered++;
            if (!data.isBlank()) {
                first = data;
            }
        }
        if (first == null) {
            return "";
        }
        out.append(first);
        while (iterableDataVariablesAccessor.hasNext() && rendered < cap) {
            final var output = renderItem(context, itemAccessor, item, iterableDataVariablesAccessor, skip, rendered);
            rendered++;
            if (!output.isBlank()) {
                out.append('\n').append(output);
            }
        }
        return out.toString();
    }

    private String renderItem(final RenderContext context, final Accessor itemAccessor, final Part item,
                              final IterableDataVariablesAccessor iterator, final int skip, final int rendered) {
        final var itemData = iterator.next();
        final var index = skip + rendered;
        final var itemContext = context.child(itemData, itemAccessor, bind(itemData, index));
        final var data = item.apply(itemContext, itemData);
        iterator.onNext();
        return data;
    }

    private Map<String, Object> bind(final Object itemData, final int index) {
        if (blockParams.isEmpty()) {
            return Map.of();
        }
        final var out = new HashMap<String, Object>(blockParams.size());
        // handlebars.js semantics: for map iteration the value is the entry value and the key the entry key,
        // for collection iteration the value is the item and the key the 0-based index
        out.put(blockParams.get(0), itemData instanceof Map.Entry<?, ?> entry ? entry.getValue() : itemData);
        if (blockParams.size() > 1) {
            out.put(blockParams.get(1), itemData instanceof Map.Entry<?, ?> entry ? entry.getKey() : index);
        }
        return out;
    }

    private int asInt(final Object value, final String name) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.strip());
            } catch (final NumberFormatException nfe) {
                throw new IllegalArgumentException(name + " must be a number but was '" + s + "'", nfe);
            }
        }
        throw new IllegalArgumentException(name + " must be a number but was " + value);
    }
}