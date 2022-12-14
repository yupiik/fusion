package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public interface FusionBean<T> {
    /**
     * @return bean raw type (to match during the bean lookup).
     */
    Type type();

    /**
     * @param container container the bean is looked up from.
     * @param dependents list you can add dependencies to release when the instance will be cleaned up.
     * @return the bean instance (with injections filled).
     */
    T create(RuntimeContainer container, List<Instance<?>> dependents);

    /**
     * @param container container the bean instance was looked up.
     * @param instance  instance to destroy/clean up.
     */
    default void destroy(final RuntimeContainer container, final T instance) {
        // no-op
    }

    /**
     * @param container container the bean instance was looked up.
     * @param dependents list you can add dependencies to release when the instance will be cleaned up.
     * @param instance  instance to fill injections to.
     */
    default void inject(final RuntimeContainer container, final List<Instance<?>> dependents, final T instance) {
        // no-op
    }

    /**
     * @return scope marker of the bean.
     */
    default Class<?> scope() {
        return DefaultScoped.class;
    }

    /**
     * @return the priority of the bean in the case of a list injection.
     */
    default int priority() {
        return 1000;
    }

    /**
     * Bean metadata.
     * <p>
     * Strictly speaking it is an open storage about the bean, can really be anything.
     * <p>
     * A sample usage is to provide to context a subclass usable to wrap the actual instance.
     * Can also be used to add interceptors if you add an interceptor implementation support.
     *
     * @return enables to hold more information for integrations.
     */
    default Map<String, Object> data() {
        return Map.of();
    }
}
