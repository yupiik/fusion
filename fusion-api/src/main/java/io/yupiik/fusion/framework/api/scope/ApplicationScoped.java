package io.yupiik.fusion.framework.api.scope;

import io.yupiik.fusion.framework.build.api.container.DetectableContext;
import io.yupiik.fusion.framework.build.api.container.LazyContext;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@LazyContext
@DetectableContext
@Retention(SOURCE)
@Target({TYPE, METHOD})
public @interface ApplicationScoped {
}
