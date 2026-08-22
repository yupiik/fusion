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

/**
 * {@code completion zsh}: prints a zsh completion script.
 */
public class CompletionZshCommand extends BaseCompletionCliCommand {
    @Override
    protected String[] path() {
        return new String[]{"completion", "zsh"};
    }

    @Override
    protected String description() {
        return "Print a zsh completion script. " +
                "Usage: save the output as \"_<app>\" in a fpath directory (e.g. ~/.zfunc/_<app>) " +
                "and add fpath+autoload -Uz compinit && compinit.";
    }

    @Override
    protected void write(final List<CliCommand<? extends Runnable>> commands, final Configuration configuration) {
        System.out.print(zshScript(commands, appName(configuration)));
    }

    protected String zshScript(final List<CliCommand<? extends Runnable>> commands, final String app) {
        final var fn = "_" + app.replace('-', '_');
        final var out = new StringBuilder();
        out.append("#compdef ").append(app).append('\n');
        out.append("# zsh completion for ").append(app).append('\n');
        out.append(fn).append("() {\n");
        out.append("    local -a cmds opts\n");
        out.append("    local curcontext=\"$curcontext\" state line\n");
        out.append("    typeset -A opt_args\n");
        out.append("    cmds=(\n");
        for (final var command : commands) {
            out.append("        '").append(escape(commandPath(command))).append(":").append(escape(command.description())).append("'\n");
        }
        out.append("    )\n");
        out.append("    _arguments -C \\\n");
        out.append("        '1: :->command'\n");
        out.append("        '*::arg:->args'\n");
        out.append("    _describe 'command' cmds -Q\n");
        out.append("    return 1\n");
        out.append("}\n");
        out.append("compdef ").append(fn).append(' ').append(app).append('\n');
        return out.toString();
    }

    private static String escape(final String value) {
        return value == null ? "" : value.replace("'", "'\\''").replace("\n", " ");
    }
}