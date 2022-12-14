package io.yupiik.fusion.framework.build.api.scanning;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a field as an injection and implicitly the enclosing bean as a @{@link Bean}.
 * <p>
 * Field scope shouldn't be private.
 */
@Target(FIELD)
@Retention(SOURCE)
public @interface Injection {
}
