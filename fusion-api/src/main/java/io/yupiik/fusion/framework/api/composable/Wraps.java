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
package io.yupiik.fusion.framework.api.composable;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.function.Function.identity;

/**
 * Utility class enabling to wrap an execution with kind of interceptors.
 */
public final class Wraps {
    private Wraps() {
        // no-op
    }

    @SafeVarargs
    public static <T> T wrap(final Supplier<T> execution, final Function<Supplier<T>, Supplier<T>>... sortedInterceptors) {
        return merge(sortedInterceptors)
                .apply(execution)
                .get();
    }

    @SafeVarargs
    public static void wrap(final Runnable execution,
                            // reusable interceptors are supplier wrappers so we keep that signature even for runnables
                            final Function<Supplier<Void>, Supplier<Void>>... sortedInterceptors) {
        merge(sortedInterceptors)
                .apply(() -> {
                    execution.run();
                    return null;
                })
                .get();
    }

    private static <T> Function<Supplier<T>, Supplier<T>> merge(final Function<Supplier<T>, Supplier<T>>[] interceptors) {
        return Stream.of(interceptors).reduce(identity(), (w1, w2) -> w2.andThen(w1) /*reverse order to enable a linear definition*/);
    }
}
