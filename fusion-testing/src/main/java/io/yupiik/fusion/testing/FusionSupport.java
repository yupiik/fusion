package io.yupiik.fusion.testing;

import io.yupiik.fusion.testing.impl.FusionPerClassLifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@ExtendWith(FusionPerClassLifecycle.class)
public @interface FusionSupport {
}
