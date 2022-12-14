package io.yupiik.fusion.framework.api.container.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// intended to host utilities for generation if needed (to reduce generated code source size)
public abstract class BaseBean<A> implements FusionBean<A> {
    private final Type type;
    private final Class<?> scope;
    private final int priority;
    private final Map<String, Object> data;

    protected BaseBean(final Type type, final Class<?> scope, final int priority, final Map<String, Object> data) {
        this.type = type;
        this.scope = scope;
        this.priority = priority;
        this.data = data;
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

    protected <A, T> T lookups(final RuntimeContainer container,
                               final Class<A> type,
                               final Function<List<Instance<A>>, T> postProcessor,
                               final List<Instance<?>> deps) {
        final var i = container.lookups(type, postProcessor);
        deps.add(i);
        return i.instance();
    }

    protected Object lookup(final RuntimeContainer container, final Type type, final List<Instance<?>> deps) {
        final var i = container.lookup(type);
        deps.add(i);
        return i.instance();
    }

    protected <T> T lookup(final RuntimeContainer container, final Class<T> type, final List<Instance<?>> deps) {
        return type.cast(lookup(container, (Type) type, deps));
    }
}
