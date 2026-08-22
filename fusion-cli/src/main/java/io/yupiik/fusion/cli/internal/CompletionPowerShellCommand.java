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
 * {@code completion powershell}: prints a PowerShell completion script.
 */
public class CompletionPowerShellCommand extends BaseCompletionCliCommand {
    @Override
    protected String[] path() {
        return new String[]{"completion", "powershell"};
    }

    @Override
    protected String description() {
        return "Print a PowerShell completion script. " +
                "Usage: add the output to your PowerShell profile, or run \"<app> completion powershell | Out-String | Invoke-Expression\".";
    }

    @Override
    protected void write(final List<CliCommand<? extends Runnable>> commands, final Configuration configuration) {
        System.out.print(powershellScript(commands, appName(configuration)));
    }

    protected String powershellScript(final List<CliCommand<? extends Runnable>> commands, final String app) {
        final var out = new StringBuilder();
        out.append("# PowerShell completion for ").append(app).append('\n');
        out.append("Register-ArgumentCompleter -Native -CommandName '").append(escape(app)).append("' -ScriptBlock {\n");
        out.append("    param($wordToComplete, $commandAst, $cursorPosition)\n");
        out.append("    $elements = @($commandAst.CommandElements | Select-Object -Skip 1)\n");
        out.append("    $tokens = @($elements[0..($elements.Count - 2)]) -join ' '\n");
        out.append("    $leaf = @'").append('\n');
        for (final var command : commands) {
            out.append("    ").append(escape(commandPath(command))).append('\n');
        }
        out.append("'@ -split \"`n\" | Where-Object { $_ -match '\\S' }\n");
        out.append("    if ($tokens -in $leaf) {\n");
        out.append("        $opts = & { switch ($tokens) {\n");
        for (final var command : commands) {
            if (!optionNames(command).isEmpty()) {
                out.append("            '").append(escape(commandPath(command))).append("' { '")
                        .append(String.join("' '", optionNames(command))).append("' }\n");
            }
        }
        out.append("        } }\n");
        out.append("        $opts | Where-Object { $_ -like \"$wordToComplete*\" } | ForEach-Object { ")
                .append("[System.Management.Automation.CompletionResult]::new($_, $_, 'ParameterName', $_) }\n");
        out.append("    } elseif ($tokens -eq '' -and $wordToComplete) {\n");
        out.append("        $leaf | ForEach-Object { if($_ -like \"$wordToComplete*\") { ")
                .append("[System.Management.Automation.CompletionResult]::new(($_ -split ' ')[0], ($_ -split ' ')[0], ")
                .append("'ParameterName', $_) } }\n");
        out.append("    } else {\n");
        out.append("        $prefix = if($tokens) { \"$tokens \" } else { '' }\n");
        out.append("        $leaf | Where-Object { $_ -like \"$prefix*\" } | ForEach-Object { ")
                .append("$rest = $_.Substring($prefix.Length); $seg = ($rest -split ' ')[0]; ")
                .append("if($seg -like \"$wordToComplete*\") { [System.Management.Automation.CompletionResult]::new($seg, ")
                .append("$seg, 'Command', $_) } }\n");
        out.append("    }\n");
        out.append("}\n");
        return out.toString();
    }

    private static String escape(final String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}