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
package io.yupiik.fusion.cli.internal;

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class CliModule implements FusionModule {
    @Override
    public Stream<FusionBean<?>> beans() {
        return Stream.concat(
                Stream.of(new CliAwaiterBean()),
                resolveShells().stream().map(CliShell::newCommand));
    }

    private static List<CliShell> resolveShells() {
        return CliShell.select(
                resolve("fusion.cli.shell", "FUSION_CLI_SHELL"),
                System.getenv("SHELL"),
                isWindows());
    }

    private static String resolve(final String property, final String env) {
        final var value = System.getProperty(property);
        return value != null && !value.isBlank() ? value : System.getenv(env);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
