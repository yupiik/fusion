package io.yupiik.fusion.framework.build.api.container;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * Mark a context marker to ensure it can be discovered automatically at generation time.
 */
@Retention(CLASS)
@Target(ANNOTATION_TYPE)
public @interface DetectableContext {
}
