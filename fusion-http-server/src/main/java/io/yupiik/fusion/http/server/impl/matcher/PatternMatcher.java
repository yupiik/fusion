package io.yupiik.fusion.http.server.impl.matcher;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternMatcher<A> implements Predicate<A> {
    private final Function<A, String> accessor;
    private final Pattern pattern;
    private final BiConsumer<A, Matcher> onMatch;

    public PatternMatcher(final Function<A, String> accessor,
                          final String pattern,
                          final BiConsumer<A, Matcher> onMatch) {
        this.accessor = accessor;
        this.pattern = Pattern.compile(pattern);
        this.onMatch = onMatch;
    }

    @Override
    public boolean test(final A a) {
        final var value = accessor.apply(a);
        if (value == null) {
            return false;
        }
        final var matcher = pattern.matcher(value);
        if (matcher.matches()) {
            onMatch.accept(a, matcher);
            return true;
        }
        return false;
    }
}
