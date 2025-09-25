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
package test.p;


import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

import java.util.List;

@Command(name = "nc", description = "A super command.")
public class NestedCommand implements Runnable {
    public NestedCommand(final Conf conf) {
        // no-op
    }

    @Override
    public void run() {
        // no-op
    }

    @RootConfiguration("c1")
    public record Conf(Nested1 first, List<Nested2> nested) {
    }

    public record Nested1(String lower) {
    }

    public record Nested2(Nested3 other) {
    }

    public record Nested3(String lower) {
    }
}