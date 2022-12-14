package io.yupiik.fusion.framework.build.api.configuration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Customize a configuration class.
 */
@Target(PARAMETER)
@Retention(SOURCE)
public @interface Property {
    /**
     * @return name of the property - else the field/member name is used.
     */
    String value() default "";

    /**
     * @return {@code true} if it should fail at runtime if the value if missing.
     */
    boolean required() default false;

    /**
     * @return some comment about the property goal/intent/usage.
     */
    String documentation() default "";
}
