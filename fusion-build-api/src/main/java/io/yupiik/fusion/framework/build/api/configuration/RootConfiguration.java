package io.yupiik.fusion.framework.build.api.configuration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a class as being an instantiable configuration.
 * <p>
 * For records all constructor (the most public one and with the most parameters) members are used.
 * <p>
 * You can use @{@link Property} to tweak the lookup of the values in the context and document the configuration.
 * <p>
 * IMPORTANT: pojo are not (yet) supported so ensure to define a record.
 * <p>
 * The value are read using {@code io.yupiik.fusion.framework.api.configuration.Configuration} API.
 * The key starts with the {@link RootConfiguration#value()} (or simple name of the class if not set) then properties are appended separated by dots.
 * For lists, it uses comma separated values for primitives but an indexed prefix for nested objects ({@code prefix.nestedList.0.nestedObjectMember=xxx}).
 * In this last case you need to set {@code prefix.nestedList.length=N} value to ensure the instantiator creates the right list.
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface RootConfiguration {
    /**
     * @return prefix name using properties syntax. If not set the simple class name is used.
     */
    String value() default "";
}
