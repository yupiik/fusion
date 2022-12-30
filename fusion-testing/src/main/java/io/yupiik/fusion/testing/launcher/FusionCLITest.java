package io.yupiik.fusion.testing.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Mark a test as using Launcher.
 * Generally useful for CLI application where an awaiter implement the CLI injecting
 * {@link io.yupiik.fusion.framework.api.main.Args}.
 */
@Test
@Target(METHOD)
@Retention(RUNTIME)
@ExtendWith(FusionCLITestExtension.class)
public @interface FusionCLITest {
    /**
     * @return CLI args.
     */
    String[] args() default {};

    /**
     * @return should stdout be captured and injectable as {@link Stdout}.
     */
    boolean captureStdOut() default true;

    /**
     * @return should stdout be captured and injectable as {@link Stderr}.
     */
    boolean captureStderr() default true;
}
