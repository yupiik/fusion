package io.yupiik.fusion.framework.api;

import io.yupiik.fusion.framework.api.container.FusionBean;

/**
 * A looked up instance (instantiated bean).
 * @param <T> the bean type.
 */
public interface Instance<T> extends AutoCloseable {
    /**
     * @return the bean this instance was created from if any, else {@code null}.
     */
    FusionBean<T> bean();

    /**
     * @return the instance itself.
     */
    T instance();

    /**
     * Release the instance (and potentially dependencies).
     */
    @Override
    default void close() {
        // no-op
    }
}
