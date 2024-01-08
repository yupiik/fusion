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
