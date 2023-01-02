/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
