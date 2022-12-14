package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;

import java.util.List;

public class DefaultInstance<T> implements Instance<T> {
    private final FusionBean<T> bean;
    private final RuntimeContainer container;
    private final T instance;
    private final List<io.yupiik.fusion.framework.api.Instance<?>> dependencies;

    public DefaultInstance(final FusionBean<T> bean, final RuntimeContainer container,
                           final T instance, final List<io.yupiik.fusion.framework.api.Instance<?>> dependencies) {
        this.bean = bean;
        this.container = container;
        this.instance = instance;
        this.dependencies = dependencies;
    }

    @Override
    public FusionBean<T> bean() {
        return bean;
    }

    @Override
    public T instance() {
        return instance;
    }

    @Override
    public synchronized void close() {
        if (bean != null) {
            bean.destroy(container, instance);
        }

        if (dependencies.isEmpty()) {
            return;
        }

        RuntimeException error = null;
        for (final var dep : dependencies) {
            try {
                dep.close();
            } catch (final Exception re) {
                if (error == null) {
                    error = new IllegalStateException("Can't close properly dependencies of " + instance);
                }
                error.addSuppressed(re);
            }
        }
        dependencies.clear();
        if (error != null) {
            throw error;
        }
    }
}
