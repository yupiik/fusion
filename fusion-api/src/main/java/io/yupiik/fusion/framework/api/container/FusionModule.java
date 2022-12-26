package io.yupiik.fusion.framework.api.container;

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.spi.FusionContext;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public interface FusionModule {
    /**
     * @return module priority.
     */
    default int priority() {
        return 1000;
    }

    /**
     * @return the stream of beans to register.
     */
    default Stream<FusionBean<?>> beans() {
        return Stream.empty();
    }

    /**
     * @return the stream of contexts to register.
     */
    default Stream<FusionContext> contexts() {
        return Stream.empty();
    }

    /**
     * @return the stream of event listeners to register.
     */
    default Stream<FusionListener<?>> listeners() {
        return Stream.empty();
    }

    default Map<String, Object> data() {
        return Map.of();
    }

    /**
     * IMPORTANT: you cannot use lookup functions yet on the container until you know it will work (priority can help).
     * <p>
     * Enables to replace easily a bean.
     *
     * @return null if no filter should be applied (faster than an always true predicate) or a predicate filtering beans.
     */
    default BiPredicate<RuntimeContainer, FusionBean<?>> beanFilter() {
        return null;
    }

    /**
     * IMPORTANT: you cannot use lookup functions yet on the container until you know it will work (priority can help).
     * <p>
     * Enables to replace a context if needed.
     *
     * @return null if no filter should be applied (faster than an always true predicate) or a predicate filtering beans.
     */
    default BiPredicate<RuntimeContainer, FusionContext> contextFilter() {
        return null;
    }

    /**
     * IMPORTANT: you cannot use lookup functions yet on the container until you know it will work (priority can help).
     * <p>
     * Enables to replace a listener or disable it easily.
     *
     * @return null if no filter should be applied (faster than an always true predicate) or a predicate filtering beans.
     */
    default BiPredicate<RuntimeContainer, FusionListener<?>> listenerFilter() {
        return null;
    }
}
