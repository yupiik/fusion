package io.yupiik.fusion.framework.build.api.json;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a record as a JSON model (optional if it contains any @{@link JsonProperty} else required to handle the marked type).
 */
@Retention(SOURCE)
@Target(TYPE)
public @interface JsonModel {
}
