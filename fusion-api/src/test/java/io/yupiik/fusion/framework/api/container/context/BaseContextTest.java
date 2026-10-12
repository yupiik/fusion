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
package io.yupiik.fusion.framework.api.container.context;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.DefaultInstance;
import io.yupiik.fusion.framework.api.container.FusionBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseContextTest {
    static final class Exposed extends BaseContext {
        @Override
        public Class<?> marker() {
            return Object.class;
        }

        @Override
        public <T> Instance<T> getOrCreate(final RuntimeContainer container, final FusionBean<T> bean) {
            throw new UnsupportedOperationException();
        }

        List<Instance<?>> sort(final List<Instance<?>> instances) {
            return sortByDestroyOrder(instances.stream()).collect(java.util.stream.Collectors.toList());
        }
    }

    private final Exposed context = new Exposed();

    private static Instance<?> wrapped(final int destroyPriority) {
        final FusionBean<Object> bean = new FusionBean<>() {
            @Override
            public Type type() {
                return Object.class;
            }

            @Override
            public Object create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                return new Object();
            }

            @Override
            public int destroyPriority() {
                return destroyPriority;
            }
        };
        return new DefaultInstance<>(bean, null, new Object(), List.of());
    }

    @Test
    void emptyIsReturnedEmpty() {
        assertEquals(List.of(), context.sort(new ArrayList<>()));
    }

    @Test
    void allZeroKeepsOrder() {
        final var instances = new ArrayList<>(List.of(wrapped(0), wrapped(0), wrapped(0)));
        // all zero-priority instances keep their relative order
        assertEquals(instances, context.sort(instances));
    }

    @Test
    void ordersByDestroyPriorityWithZeroInMiddle() {
        // lower first; negative before zero (preserving order), zero preserved, positive after
        final var a = wrapped(2);
        final var b = wrapped(-1);
        final var c = wrapped(0);
        final var d = wrapped(1);
        final var e = wrapped(0);
        final var ordered = context.sort(new ArrayList<>(List.of(a, b, c, d, e)));
        assertEquals(List.of(b, c, e, d, a), ordered);
    }

    @Test
    void nullBeanTreatedAsZeroPriority() {
        final Instance<?> noBean = new Instance<>() {
            @Override
            public FusionBean<Object> bean() {
                return null;
            }

            @Override
            public Object instance() {
                return new Object();
            }
        };
        final var negative = wrapped(-1);
        final var positive = wrapped(2);
        final var ordered = context.sort(new ArrayList<>(List.of(negative, noBean, positive)));
        assertEquals(List.of(negative, noBean, positive), ordered);
    }
}