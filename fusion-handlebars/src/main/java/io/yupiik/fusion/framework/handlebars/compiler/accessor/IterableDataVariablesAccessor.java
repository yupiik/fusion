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
package io.yupiik.fusion.framework.handlebars.compiler.accessor;

import io.yupiik.fusion.framework.handlebars.spi.Accessor;

import java.util.Iterator;

public class IterableDataVariablesAccessor implements Accessor, Iterator<Object> {
    private final Iterator<?> iterator;
    private int index = 0;
    private final Accessor delegate;
    private Object current;

    public IterableDataVariablesAccessor(final Iterator<?> iterator, final Accessor delegate) {
        this.iterator = iterator;
        this.delegate = delegate;
    }

    public void onNext() {
        index++;
    }

    @Override
    public Object find(final Object data, final String name) {
        return switch (name) {
            case "@first" -> index == 0;
            case "@index" -> index;
            case "@last" -> !iterator.hasNext();
            default -> {
                final var extracted = delegate.find(data, name);
                yield extracted == null ? delegate.find(current, name) : extracted;
            }
        };
    }

    public IterableDataVariablesAccessor of(final Accessor delegate) {
        return new IterableDataVariablesAccessor(iterator, delegate);
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public Object next() {
        return current = iterator.next();
    }
}
