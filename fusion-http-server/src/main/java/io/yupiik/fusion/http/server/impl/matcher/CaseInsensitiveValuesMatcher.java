package io.yupiik.fusion.http.server.impl.matcher;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

public class CaseInsensitiveValuesMatcher<A, B> implements Predicate<A> {
    private final Function<A, B> accessor;
    private final Set<String> expectedValues;

    public CaseInsensitiveValuesMatcher(final Function<A, B> accessor, final String... expectedValues) {
        this.accessor = accessor;
        this.expectedValues = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        this.expectedValues.addAll(Set.of(expectedValues));
    }

    @Override
    public boolean test(final A a) {
        final var value = accessor.apply(a);
        return expectedValues.stream().anyMatch(m -> Objects.equals(m, value));
    }
}
