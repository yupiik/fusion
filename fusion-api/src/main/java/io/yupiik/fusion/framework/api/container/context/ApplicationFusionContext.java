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
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext;
import io.yupiik.fusion.framework.api.container.context.subclass.SupplierDelegatingContext;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

public class ApplicationFusionContext extends DefaultFusionContext implements FusionContext, AutoCloseable {
    private final Map<FusionBean<?>, ApplicationInstance<?>> instances = new ConcurrentHashMap<>();

    @Override
    public Class<?> marker() {
        return ApplicationScoped.class;
    }

    public void clean(final FusionBean<?> bean) {
        final ApplicationInstance<?> instance = instances.remove(bean);
        if (instance != null) {
            instance.close();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Instance<T> getOrCreate(final RuntimeContainer container, final FusionBean<T> bean) {
        final var fastExisting = instances.get(bean); // do NOT use computeIfAbsent since it can be recursive and therefore fail
        if (fastExisting != null) {
            return (Instance<T>) fastExisting;
        }

        if (bean instanceof BaseBean<T> bb) {
            bb.getLock().lock();
            try {
                return doGetOrCreateInstance(container, bean);
            } finally {
                bb.getLock().unlock();
            }
        }

        // else we have to synchronized and potentially break virtual threads - unlikely
        synchronized (bean) { // beans are singleton for a container so we can lock on them securely, in particular there in app ctx
            return doGetOrCreateInstance(container, bean);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> doGetOrCreateInstance(final RuntimeContainer container, final FusionBean<T> bean) {
        final var existing = instances.get(bean);
        if (existing != null) {
            return (Instance<T>) existing;
        }

        // don't create the instance directly, ensure it is created lazily at need
        final var subclass = (Function<DelegatingContext<T>, T>) bean.data().get("fusion.framework.subclasses.delegate");
        final var created = new ApplicationInstance<>(subclass, () -> super.getOrCreate(container, bean), bean);
        instances.put(bean, created);
        return created;
    }

    @Override
    public void close() {
        final var error = new IllegalStateException("Can't release all singletons, see suppressed exceptions for details");
        instances.values().stream().map(i -> i.real).filter(Objects::nonNull).forEach(i -> {
            try {
                i.close();
            } catch (final RuntimeException re) {
                error.addSuppressed(re);
            }
        });
        instances.clear();
        if (error.getSuppressed().length > 0) {
            throw error;
        }
    }

    private static class ApplicationInstance<T> implements Instance<T> {
        private final Supplier<Instance<T>> factory;
        private final FusionBean<T> bean;
        private final Lock lock = new ReentrantLock();
        private final T proxy;
        private volatile Instance<T> real;

        private ApplicationInstance(final Function<DelegatingContext<T>, T> subclassFactory,
                                    final Supplier<Instance<T>> real, final FusionBean<T> bean) {
            this.factory = real;
            this.bean = bean;
            if (subclassFactory != null) { // the instance is created at first call (most lazy possible)
                this.proxy = subclassFactory.apply(new SupplierDelegatingContext<>(() -> ensureDelegate().instance()));
            } else { // instance is created at first read (generally injection time so way earlier)
                this.proxy = null;
            }
        }

        private Instance<T> ensureDelegate() {
            if (real != null) {
                return real;
            }
            lock.lock();
            try {
                if (real == null) {
                    real = factory.get();
                }
            } finally {
                lock.unlock();
            }
            return real;
        }

        @Override
        public FusionBean<T> bean() {
            return bean;
        }

        @Override
        public T instance() {
            return proxy == null ? ensureDelegate().instance() : proxy;
        }

        @Override
        public void close() {
            // no-op, will be done with context destruction
        }
    }
}
