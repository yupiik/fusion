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

import io.yupiik.fusion.framework.api.configuration.Configuration;

import java.util.List;
import java.util.stream.Stream;

/**
 * {@code completion bash}: prints a bash completion script.
 */
public class CompletionBashCommand extends BaseCompletionCliCommand {
    @Override
    protected String[] path() {
        return new String[]{"completion", "bash"};
    }

    @Override
    protected String description() {
        return "Print a bash completion script. " +
                "Usage: <app> completion bash > /etc/bash_completion.d/<app> (then source it, or eval \"$(<app> completion bash)\").";
    }

    @Override
    protected void write(final List<CliCommand<? extends Runnable>> commands, final Configuration configuration) {
        final var line = configuration.get("comp.line").orElse(null);
        final var point = configuration.get("comp.point").map(Integer::parseInt).orElse(null);
        if (line == null || point == null) {
            System.out.print(bashScript(commands, appName(configuration)));
            return;
        }
        candidatesFor(line, point, commands).forEach(System.out::println);
    }

    protected String bashScript(final List<CliCommand<? extends Runnable>> commands, final String app) {
        final var fn = funcName(app);
        final var out = new StringBuilder();
        out.append("# bash completion for ").append(app).append('\n');
        out.append(fn).append("() {\n");
        out.append("    local cur path line seg\n");
        out.append("    COMPREPLY=()\n");
        out.append("    cur=\"${COMP_WORDS[COMP_CWORD]}\"\n");
        out.append("    path=\"\"\n");
        out.append("    local i\n");
        out.append("    for ((i=1;i<COMP_CWORD;i++)); do\n");
        out.append("        path=\"${path}${path:+ }${COMP_WORDS[i]}\"\n");
        out.append("    done\n");
        out.append("    while IFS= read -r line; do\n");
        out.append("        if [[ \"$line\" == \"$path\" ]]; then\n");
        out.append("            COMPREPLY=( $(compgen -W \"$(").append(fn).append("_opts \"$line\")\" -- \"$cur\") )\n");
        out.append("            return 0\n");
        out.append("        fi\n");
        out.append("    done < <(").append(fn).append("_cmds)\n");
        out.append("    local out=\"\"\n");
        out.append("    while IFS= read -r line; do\n");
        out.append("        if [[ -z \"$path\" ]]; then seg=\"${line%% *}\";\n");
        out.append("        elif [[ \"$line\" == \"$path \"* ]]; then seg=\"${line#$path }\"; seg=\"${seg%% *}\";\n");
        out.append("        else continue; fi\n");
        out.append("        [[ \"$seg\" == \"$cur\"* && \" $out \" != *\" $seg \"* ]] && out=\"$out $seg\"\n");
        out.append("    done < <(").append(fn).append("_cmds)\n");
        out.append("    COMPREPLY=( $(compgen -W \"$out\" -- \"$cur\") )\n");
        out.append("    return 0\n");
        out.append("}\n");
        out.append(fn).append("_cmds() {\n");
        for (final var command : commands) {
            out.append("    printf '%s\\n' '").append(escape(commandPath(command))).append("'\n");
        }
        out.append("}\n");
        out.append(fn).append("_opts() {\n");
        out.append("    case \"$1\" in\n");
        for (final var command : commands) {
            out.append("        '").append(escape(commandPath(command))).append("') printf '%s\\n' ");
            final var options = optionNames(command);
            if (options.isEmpty()) {
                out.append("'' ;;");
            } else {
                options.forEach(opt -> out.append('\'').append(escape(opt)).append("' "));
                out.append(";;");
            }
            out.append('\n');
        }
        out.append("    esac\n");
        out.append("}\n");
        out.append("complete -F ").append(fn).append(' ').append(app).append('\n');
        return out.toString();
    }

    private static String escape(final String value) {
        return value == null ? "" : value.replace("'", "'\\''");
    }

    protected List<String> candidatesFor(final String line, final int point, final List<CliCommand<? extends Runnable>> commands) {
        final var trimmed = line.length() < point ? line : line.substring(0, Math.max(point, 0));
        if (!trimmed.isEmpty() && trimmed.charAt(trimmed.length() - 1) == ' ') {
            final var prefix = trimmed.isEmpty() ? "" : trimmed.substring(0, trimmed.length() - 1);
            return candidates(commands, splitWords(prefix), "");
        }
        final var sp = trimmed.lastIndexOf(' ');
        final var cur = sp < 0 ? trimmed : trimmed.substring(sp + 1);
        return candidates(commands, splitWords(sp < 0 ? "" : trimmed.substring(0, sp)), cur);
    }

    private static List<String> splitWords(final String text) {
        if (text.isBlank()) {
            return List.of();
        }
        return Stream.of(text.split("\\s+")).toList();
    }
}