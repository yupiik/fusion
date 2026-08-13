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

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.configuration.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface CliCommand<C extends Runnable> {
    String name();

    /**
     * @return the command path segments, each segment is one leading CLI argument.
     */
    default String[] path() {
        return name().split("/");
    }

    /**
     * @return the option prefix used for the short-name resolution and help display,
     * e.g. {@code deploy/run} maps to {@code --deploy-run-}.
     */
    default String cliPrefix() {
        return "--" + String.join("-", path()) + "-";
    }

    String description();

    List<Parameter> parameters();

    Instance<C> create(Configuration configuration, List<Instance<?>> dependents);

    default Map<String, String> metadata() {
        return Map.of();
    }

    default Function<String, String> keyMapper() {
        return Function.identity();
    }

    record Parameter(String configName, String cliName, String description) {}
}
