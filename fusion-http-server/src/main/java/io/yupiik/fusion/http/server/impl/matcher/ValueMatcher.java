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
