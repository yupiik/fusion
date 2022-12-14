package io.yupiik.fusion.framework.api.scope;

import io.yupiik.fusion.framework.build.api.container.DetectableContext;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

// equivalent of CDI dependent (but intended to be even more implicit)
@DetectableContext
@Retention(SOURCE)
@Target({TYPE, METHOD})
public @interface DefaultScoped {
}
