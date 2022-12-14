package io.yupiik.fusion.http.server.impl.matcher;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public class ValueMatcher<A, B> implements Predicate<A> {
    private final Function<A, B> accessor;
    private final B expectedValue;
    private final BiPredicate<B, B> tester;

    public ValueMatcher(final Function<A, B> accessor, final B expectedValue, final BiPredicate<B, B> tester) {
        this.accessor = accessor;
        this.expectedValue = expectedValue;
        this.tester = tester;
    }

    public ValueMatcher(final Function<A, B> accessor, final B expectedValue) {
        this(accessor, expectedValue, Objects::equals);
    }

    @Override
    public boolean test(final A a) {
        final var value = accessor.apply(a);
        return value != null && tester.test(value, expectedValue);
    }
}
