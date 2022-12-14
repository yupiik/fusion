package io.yupiik.fusion.framework.build.api.event;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a method parameter which is then considered as a listener.
 * <p>
 * Important: for now other parameters are not supported, use field injections to get bean instances.
 */
@Retention(SOURCE)
@Target(PARAMETER)
public @interface OnEvent {
}
