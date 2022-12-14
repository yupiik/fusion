package io.yupiik.fusion.framework.build.api.json;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Can be used on a record parameter to refine the name of a JSON parameter serialization.
 */
@Retention(SOURCE)
@Target(PARAMETER)
public @interface JsonProperty {
    String value() default "";
}
