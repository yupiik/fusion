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

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.BaseLookup;
import io.yupiik.fusion.framework.api.container.FusionBean;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

// intended to host utilities for generation if needed (to reduce generated code source size)
public abstract class BaseBean<A> extends BaseLookup implements FusionBean<A> {
    private final Type type;
    private final Class<?> scope;
    private final int priority;
    private final Map<String, Object> data;
    private final Lock lock = new ReentrantLock();

    protected BaseBean(final Type type, final Class<?> scope, final int priority, final Map<String, Object> data) {
        this.type = type;
        this.scope = scope;
        this.priority = priority;
        this.data = data;
    }

    public Lock getLock() {
        return lock;
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public Class<?> scope() {
        return scope;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public Map<String, Object> data() {
        return data;
    }
}
