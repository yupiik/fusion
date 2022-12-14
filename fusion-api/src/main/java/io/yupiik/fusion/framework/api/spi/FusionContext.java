package io.yupiik.fusion.framework.api.spi;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;

/**
 * Defines a context, ie a way to define a scope for beans.
 */
public interface FusionContext {
    /**
     * @return the annotation which enables the scope when put on a bean. It must be marked with {@link io.yupiik.fusion.framework.build.api.container.DetectableContext}.
     */
    Class<?> marker();

    /**
     * Lookups an instance of the bean in the context.
     *
     * @param container the related framework.
     * @param bean the bean to lookup.
     * @return the instance.
     * @param <T> the instance type.
     */
    <T> Instance<T> getOrCreate(RuntimeContainer container, FusionBean<T> bean);
}
