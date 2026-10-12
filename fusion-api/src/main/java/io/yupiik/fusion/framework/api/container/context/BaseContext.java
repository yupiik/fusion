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
import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Base context hosting the destroy ordering logic shared by the {@link FusionContext} implementations.
 */
public abstract class BaseContext implements FusionContext {
    /**
     * Orders instances so that the ones with the lowest {@code destroyPriority()} are destroyed first.
     * <p>
     * A single stable sort on the {@code destroyPriority()} yields: negative priorities first (ascending), then
     * the {@code 0} priority instances preserving their relative order (stable), then positive priorities
     * (ascending). An instance without a bean is treated as a {@code 0} priority.
     *
     * @param instances the instances to order for destruction.
     * @return a stream of the ordered instances (never {@code null}).
     */
    protected Stream<Instance<?>> sortByDestroyOrder(final Stream<Instance<?>> instances) {
        return instances.sorted(Comparator.comparingInt(this::destroyPriority));
    }

    private int destroyPriority(final Instance<?> instance) {
        return instance.bean() == null ? 0 : instance.bean().destroyPriority();
    }
}