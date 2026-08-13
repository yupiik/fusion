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
 * The command name is a path of segments: {@code @Command(name = {"deploy", "run"})} declares the
 * {@code deploy run} subcommand while {@code @Command(name = "deploy/run")} declares a single literal
 * {@code deploy/run} segment. The single value shorthand is accepted for one segment.
 * <p>
 * Example:
 *
 * <pre>
 * {@code @DefaultScoped}
 * {@code @Command(name = {"deploy", "run"}, description = "....")}
 * public class MyCommand implements Runnable {
 *     public MyCommand(final MyConf conf) { ... }
 *
 *     {@code @Override}
 *     public void run() { ... }
 *
 *     {@code @RootConfiguration("deploy.run")}
 *     public record MyConf(....) {}
 * }
 * </pre>
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface Command {
    /**
     * @return command path segments (leading CLI args to select the command). A single value means a single segment.
     */
    String[] name();

    /**
     * @return command description/usage.
     */
    String description();
}
