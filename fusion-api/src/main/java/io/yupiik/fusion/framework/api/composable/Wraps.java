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
