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
package io.yupiik.fusion.tracing.collector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Buffer<T> {
    private final Map<T, T> buffer = new ConcurrentHashMap<>();
    private final AtomicInteger size = new AtomicInteger();

    public void add(final T item) {
        buffer.put(item, item);
        size.incrementAndGet();
    }

    public int size() {
        return size.get(); // faster than ConcurrentHashMap#size
    }

    // always called from a single thread
    public Collection<T> drain() {
        final var all = new ArrayList<>(buffer.keySet());
        size.addAndGet(-all.size());
        buffer.clear();
        return all;
    }
}
