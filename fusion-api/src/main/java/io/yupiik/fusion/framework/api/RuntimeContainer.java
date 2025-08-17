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
package io.yupiik.fusion.framework.api;

import io.yupiik.fusion.framework.api.container.Beans;
import io.yupiik.fusion.framework.api.container.Contexts;
import io.yupiik.fusion.framework.api.container.Listeners;
import io.yupiik.fusion.framework.api.event.Emitter;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Function;

public interface RuntimeContainer extends Emitter, AutoCloseable {
    Beans getBeans();

    Contexts getContexts();

    Listeners getListeners();

    <T> Instance<T> lookup(Class<T> type);

    <T> Instance<T> lookup(Type type);

    <A, T> Instance<T> lookups(Class<A> type,
                               Function<List<Instance<A>>, T> postProcessor);


    default <T> Instance<List<T>> lookups(final Class<T> type) {
        return lookups(type, i -> i.stream().map(Instance::instance).toList());
    }

    @Override
    void close();
}
