package io.yupiik.fusion.framework.build.api.order;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Can be used on:
 * <ul>
 *     <li>A {@link io.yupiik.fusion.framework.build.api.event.OnEvent} parameter to sort the listener in the event chaine.</li>
 *     <li>A {@link io.yupiik.fusion.framework.build.api.scanning.Bean}  (explicit or not) to sort its position in a {@link java.util.Collection} injection if not {@link Comparable}.</li>
 * </ul>
 */
@Target({PARAMETER, TYPE})
@Retention(SOURCE)
public @interface Order {
    int value();
}
