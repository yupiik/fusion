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
package io.yupiik.fusion.framework.api.spi;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;

/**
 * Defines a context, ie a way to define a scope for beans.
 */
public interface FusionContext {
    /**
     * @return the annotation which enables the scope when put on a bean. It must be marked with {@link io.yupiik.fusion.framework.build.api.container.DetectableContext}.
     */
    Class<?> marker();

    /**
     * Lookups an instance of the bean in the context.
     *
     * @param container the related framework.
     * @param bean the bean to lookup.
     * @return the instance.
     * @param <T> the instance type.
     */
    <T> Instance<T> getOrCreate(RuntimeContainer container, FusionBean<T> bean);
}
