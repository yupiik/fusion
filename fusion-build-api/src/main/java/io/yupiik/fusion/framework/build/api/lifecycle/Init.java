package io.yupiik.fusion.framework.build.api.lifecycle;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a method as being called just after ean injections.
 * <p>
 * Method scope shouldn't be private.
 */
@Target(METHOD)
@Retention(SOURCE)
public @interface Init {
}
