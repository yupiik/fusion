/*
 * Copyright (c) 2022 - present - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.fusion.testing.launcher;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Function;

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

    /**
     * @return enables to customize the container of the test.
     */
    Class<? extends Customizer> customizer() default Customizer.class;

    interface Customizer extends Function<ConfiguringContainer, ConfiguringContainer> {}
}
