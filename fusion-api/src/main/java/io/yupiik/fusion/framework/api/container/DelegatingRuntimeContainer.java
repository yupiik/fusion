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
package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

public class DelegatingRuntimeContainer implements RuntimeContainer {
    private final RuntimeContainer delegate;

    public DelegatingRuntimeContainer(final RuntimeContainer delegate) {
        this.delegate = delegate;
    }

    @Override
    public Beans getBeans() {
        return delegate.getBeans();
    }

    @Override
    public Contexts getContexts() {
        return delegate.getContexts();
    }

    @Override
    public Listeners getListeners() {
        return delegate.getListeners();
    }

    @Override
    public <T> Instance<T> lookup(final Class<T> type) {
        return delegate.lookup(type);
    }

    @Override
    public <T> Instance<T> lookup(final Type type) {
        return delegate.lookup(type);
    }

    @Override
    public <A, T> Instance<T> lookups(final Class<A> type, final Function<List<Instance<A>>, T> postProcessor) {
        return delegate.lookups(type, postProcessor);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public <T> void emit(final T event) {
        delegate.emit(event);
    }
}
