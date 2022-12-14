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
