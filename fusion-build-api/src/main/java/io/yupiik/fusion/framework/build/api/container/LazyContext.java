package io.yupiik.fusion.framework.build.api.container;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Mark a context marker to request the generation to instantiate proxies instead of the final class.
 * It avoids to load the whole object graph at injection time.
 */
@Retention(CLASS)
@Target(ANNOTATION_TYPE)
public @interface LazyContext {
}
