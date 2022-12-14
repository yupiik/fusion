package io.yupiik.fusion.framework.api.container.bean;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;

import java.lang.reflect.Type;
import java.util.List;

public class NullBean<A> implements FusionBean<A> {
    private final Type expectedType;

    public NullBean(final Type expectedType) {
        this.expectedType = expectedType;
    }

    @Override
    public Type type() {
        return expectedType;
    }

    @Override
    public A create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        return null;
    }
}
