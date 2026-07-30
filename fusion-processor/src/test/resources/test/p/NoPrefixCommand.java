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
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

@Command(name = "np", description = "A command without any configuration prefix.")
public class NoPrefixCommand implements Runnable {
    private final Conf conf;

    public NoPrefixCommand(final Conf conf) {
        this.conf = conf;
    }

    @Override
    public void run() {
        System.setProperty(NoPrefixCommand.class.getName(), "conf=" + conf);
    }

    @RootConfiguration("-")
    public record Conf(@Property(documentation = "The name.", required = true) String name, Nested nested) {
    }

    public record Nested(@Property(documentation = "Nested value.") String lower) {
    }
}
