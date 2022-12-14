package io.yupiik.fusion.framework.api.container;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Intended to ensure all loaded beans are compatible with the runtime (when needed).
 */
@Retention(RUNTIME)
public @interface Generation {
    int version();
}
