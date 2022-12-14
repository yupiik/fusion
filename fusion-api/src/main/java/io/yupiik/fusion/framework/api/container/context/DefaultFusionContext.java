package io.yupiik.fusion.framework.api.container.context;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.DefaultInstance;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.util.ArrayList;
import java.util.Collections;

public class DefaultFusionContext implements FusionContext {
    @Override
    public Class<?> marker() {
        return DefaultScoped.class;
    }

    @Override
    public <T> Instance<T> getOrCreate(final RuntimeContainer container, final FusionBean<T> bean) {
        final var dependents = new ArrayList<Instance<?>>();
        final var instance = bean.create(container, dependents);
        Collections.reverse(dependents); // destroy in reverse order
        return new DefaultInstance<>(bean, container, instance, dependents);
    }
}
