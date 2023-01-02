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
package io.yupiik.fusion.framework.api.container.instance;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.OptionalBean;

import java.util.Optional;

public class OptionalInstance<A> implements Instance<Optional<A>> {
    private final Instance<A> delegate;
    private final OptionalBean<A> bean;

    public OptionalInstance(final Instance<A> delegate) {
        this.delegate = delegate;
        this.bean = new OptionalBean<>(delegate.bean());
    }

    @Override
    public FusionBean<Optional<A>> bean() {
        return bean;
    }

    @Override
    public Optional<A> instance() {
        return Optional.of(delegate.instance());
    }

    @Override
    public void close() {
        delegate.close();
    }
}
