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
import io.yupiik.fusion.framework.api.container.configuration.source.DirectorySource;
import io.yupiik.fusion.framework.api.container.configuration.source.EnvironmentSource;
import io.yupiik.fusion.framework.api.container.configuration.source.SystemPropertiesSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class ConfigurationImpl implements Configuration {
    private final List<ConfigurationSource> sources;

    public ConfigurationImpl(final List<ConfigurationSource> sources) {
        this.sources = Stream.concat(
                        sources.stream(),
                        defaultSources())
                .toList();
    }

    private static Stream<ConfigurationSource> defaultSources() {
        final var jvmSources = Stream.of(new SystemPropertiesSource(), new EnvironmentSource());
        final var secretDirectories = System.getenv("FUSION_CONFIGURATION_SOURCES_SECRETS");
        if (secretDirectories != null) {
            final var secrets = Stream.of(secretDirectories.split(","))
                    .map(String::strip)
                    .filter(Predicate.not(String::isBlank))
                    .map(Path::of)
                    .filter(Files::isDirectory)
                    .toList();
            Logger.getLogger(ConfigurationImpl.class.getName())
                    .info(() -> "Using secret sources: "  + secrets);
            return Stream.concat(secrets.stream().map(it -> new DirectorySource("", it)), jvmSources);

        }
        if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
            final var root = Path.of("/var/run/secrets");
            if (!Files.isDirectory(root)) {
                return jvmSources;
            }

            final var logger = Logger.getLogger(ConfigurationImpl.class.getName());
            try (final var child = Files.list(root)) {
                final var result = Stream.concat(
                        child.map(it -> new DirectorySource(it.getFileName().toString() + '.', it)),
                        jvmSources);
                logger
                        .info(() -> "Running into kubernetes, enabling using '/var/run/secrets' subdirectories as secret holders ('/var/run/secrets/kubernetes.io/serviceaccount' ends available as `kubernetes.io.serviceaccount` key for example). " +
                                "To disable or customize it set FUSION_CONFIGURATION_SOURCES_SECRETS environment variable to an empty or false (disabled) or list of directories value.");
                return result;
            } catch (final IOException ioe) {
                logger.log(Level.FINER, ioe, () -> "Can't load secrets: " + ioe.getMessage());
            }
        }
        return jvmSources;
    }

    @Override
    public Optional<String> get(final String key) {
        return sources.stream()
                .map(s -> s.get(key))
                .filter(Objects::nonNull)
                .findFirst();
    }
}
