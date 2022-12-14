package io.yupiik.fusion.testing;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Mark a test parameter as injected from fusion IoC.
 */
@Target(PARAMETER)
@Retention(RUNTIME)
public @interface Fusion {
}
