package io.yupiik.fusion.framework.api.container.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;

public class ProvidedInstanceBean<T> implements FusionBean<T> {
    private final Class<?> scope;
    private final Class<T> type;
    private final Supplier<T> factory;

    public ProvidedInstanceBean(final Class<?> scope,
                                final Class<T> type,
                                final Supplier<T> factory) {
        this.scope = scope;
        this.type = type;
        this.factory = factory;
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
    public T create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return factory.get();
    }
}
