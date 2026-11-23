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
package io.yupiik.fusion.framework.api.container.bean;

import io.yupiik.fusion.framework.api.container.listener.FunctionalListener;

import io.yupiik.fusion.framework.api.container.Listeners;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FunctionalOrderingTest {
    @Test
    void listenerPriorityTiesPreserveRegistrationOrder() {
        final var listeners = new Listeners();
        final var trace = new ArrayList<String>();
        final var expected = IntStream.range(0, 40).mapToObj(i -> "listener" + i).sorted().toList();
        final var ordered = new ArrayList<String>();
        for (int i = expected.size() - 1; i >= 0; i--) {
            final var name = expected.get(i);
            ordered.add(name);
            listeners.doRegister(new FunctionalListener<>(String.class, 1000, (container, event) -> {
                trace.add(name);
            }));
        }
        listeners.doRegister(new FunctionalListener<>(String.class, 2000, (container, event) -> {
            trace.add("last");
        }));
        listeners.doFire(null, String.class, "event");
        ordered.add("last");
        assertEquals(ordered, trace);
        trace.clear();
        listeners.doFire(null, String.class, "cached");
        assertEquals(ordered, trace);
    }
}
