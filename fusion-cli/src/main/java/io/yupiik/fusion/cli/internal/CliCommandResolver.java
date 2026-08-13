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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the command path from the leading CLI arguments.
 * The path is matched segment by segment so a literal {@code deploy/run} segment (single name value)
 * is distinct from the two segments {@code deploy} and {@code run}.
 */
public class CliCommandResolver {
    private final Map<List<String>, CliCommand<? extends Runnable>> commands;

    public CliCommandResolver(final List<CliCommand<? extends Runnable>> commands) {
        this.commands = new HashMap<>();
        for (final var command : commands) {
            final var key = List.of(command.path());
            if (this.commands.putIfAbsent(key, command) != null) {
                throw new IllegalArgumentException("Duplicate command path '" + String.join("/", key) + "'");
            }
        }
    }

    /**
     * Resolves the longest exact command matching the leading tokens, else the longest implicit group
     * (a proper segment prefix of at least one registered command), else {@code null}.
     */
    public Resolution resolve(final List<String> tokens) {
        CliCommand<? extends Runnable> exact = null;
        var exactLen = 0;
        for (var i = 1; i <= tokens.size(); i++) {
            final var candidate = commands.get(tokens.subList(0, i));
            if (candidate != null) {
                exact = candidate;
                exactLen = i;
            }
        }
        if (exact != null) {
            return new Resolution(exact, exactLen, null, 0);
        }

        var groupLen = 0;
        for (var i = 1; i <= tokens.size(); i++) {
            final var segmentCount = i;
            final var candidate = tokens.subList(0, i);
            final var isPrefix = commands.keySet().stream()
                    .anyMatch(path -> path.size() > segmentCount && path.subList(0, segmentCount).equals(candidate));
            if (!isPrefix) {
                break;
            }
            groupLen = i;
        }
        if (groupLen > 0) {
            return new Resolution(null, 0, tokens.subList(0, groupLen), groupLen);
        }
        return new Resolution(null, 0, null, 0);
    }

    public record Resolution(CliCommand<? extends Runnable> command, int commandLen,
                             List<String> group, int groupLen) {
        public boolean isExact() {
            return command != null;
        }

        public boolean isGroup() {
            return group != null;
        }
    }
}
