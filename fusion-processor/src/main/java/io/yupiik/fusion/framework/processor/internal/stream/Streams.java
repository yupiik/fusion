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
package io.yupiik.fusion.framework.processor.internal.stream;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class Streams {
    private Streams() {
        // no-op
    }

    public static <A> Stream<ItemWithIndex<A>> withIndex(final Stream<? extends A> delegate) {
        final var counter = new AtomicInteger();
        return delegate.map(it -> new ItemWithIndex<>(it, counter.getAndIncrement()));
    }

    public record ItemWithIndex<A>(A item, int index) {
    }
}
