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
package io.yupiik.fusion.framework.api.container.configuration;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.api.container.configuration.source.EnvironmentSource;
import io.yupiik.fusion.framework.api.container.configuration.source.SystemPropertiesSource;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class ConfigurationImpl implements Configuration {
    private final List<ConfigurationSource> sources;

    public ConfigurationImpl(final List<ConfigurationSource> sources) {
        this.sources = Stream.concat(
                        sources.stream(),
                        Stream.of(new SystemPropertiesSource(), new EnvironmentSource()))
                .toList();
    }

    @Override
    public Optional<String> get(final String key) {
        return sources.stream()
                .map(s -> s.get(key))
                .filter(Objects::nonNull)
                .findFirst();
    }
}
