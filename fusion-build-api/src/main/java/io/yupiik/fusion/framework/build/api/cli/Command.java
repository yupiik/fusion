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
package io.yupiik.fusion.framework.build.api.cli;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a bean as being registered as a CLI command.
 * This works OOTB with the {@code Launcher} main or you would need to register a custom {@code Args} instance in the container.
 * <p>
 * The command takes a configuration ({@link io.yupiik.fusion.framework.build.api.configuration.RootConfiguration})
 * as parameter which is built from the args and implements {@link Runnable}.
 * <p>
 * Example:
 *
 * <pre>
 * {@code @DefaultScoped}
 * {@code @Command(name = "mycommand", description = "....")}
 * public class MyCommand implements Runnable {
 *     public MyCommand(final MyConf conf) { ... }
 *
 *     {@code @Override}
 *     public void run() { ... }
 *
 *     {@code @RootConfiguration("...")}
 *     public record MyConf(....) {}
 * }
 * </pre>
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface Command {
    /**
     * @return command name (first arg of the CLI in general).
     */
    String name();

    /**
     * @return command description/usage.
     */
    String description();
}
