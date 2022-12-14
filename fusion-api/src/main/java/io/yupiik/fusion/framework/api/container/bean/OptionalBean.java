package io.yupiik.fusion.framework.api.container.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

public class OptionalBean<A> implements FusionBean<Optional<A>> {
    private final FusionBean<A> delegating;

    public OptionalBean(final FusionBean<A> delegating) {
        this.delegating = delegating;
    }

    @Override
    public Type type() {
        return delegating.type();
    }

    @Override
    public Optional<A> create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return Optional.of(delegating.create(container, dependents));
    }

    @Override
    public void destroy(final RuntimeContainer container, final Optional<A> instance) {
        delegating.destroy(container, instance.orElseThrow());
    }

    @Override
    public Class<?> scope() {
        return delegating.scope();
    }

    @Override
    public int priority() {
        return delegating.priority();
    }
}
