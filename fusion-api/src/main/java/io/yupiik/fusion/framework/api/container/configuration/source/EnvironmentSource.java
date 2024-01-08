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
package io.yupiik.fusion.framework.api.container.configuration.source;

import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;

import java.util.regex.Pattern;

import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;

public class EnvironmentSource implements ConfigurationSource {
    private final Pattern posix = Pattern.compile("[^A-Za-z0-9]");

    @Override
    public String get(final String key) {
        return ofNullable(System.getenv(key))
                .or(() -> {
                    final var posixKey = posix.matcher(key).replaceAll("_");
                    return ofNullable(System.getenv(posixKey))
                            .or(() -> ofNullable(System.getenv(posixKey.toUpperCase(ROOT))));
                })
                .orElse(null);
    }
}
