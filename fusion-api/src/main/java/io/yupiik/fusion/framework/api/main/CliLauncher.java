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
package io.yupiik.fusion.framework.api.main;

import java.util.List;
import java.util.stream.Stream;

/**
 * Same as launcher but skips first parameter for args source so parameters of the CLI command can be configuration entries.
 */
public class CliLauncher extends Launcher {
    public CliLauncher(final String... args) {
        super(args);
    }

    @Override
    protected List<String> prepareArgs(final String[] args) {
        return args.length > 0 ? Stream.of(args).skip(1).toList() : List.of();
    }

    public static void main(final String... args) {
        try (final var launcher = new CliLauncher(args)) {
            launcher.doMain(launcher);
        }
    }
}
