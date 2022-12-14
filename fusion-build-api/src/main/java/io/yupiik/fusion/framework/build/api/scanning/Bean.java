package io.yupiik.fusion.framework.build.api.scanning;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a bean without any injection as a bean when put on a class.
 * <p>
 * When put on a method, it enables the method to be used as a factory.
 * If the returned type implements {@link AutoCloseable} then the {@link AutoCloseable#close()} method is called to destroy the bean.
 * Note that the enclosing class MUST be a bean too - the instance is used to call the method - until the method is static.
 * Lastly, two producers can't use the same method name in the same class even if their signature is different.
 */
@Retention(SOURCE)
@Target({TYPE, METHOD})
public @interface Bean {
}
