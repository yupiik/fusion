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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toMap;

// can be used for k8s secrets for example
// note it is immutable for now since config reloading triggers consistency issues in apps.
// if needed it can use a custom source (facading this one with cache for ex)
public class DirectorySource implements ConfigurationSource {
    private final Map<String, String> values = new HashMap<>();

    public DirectorySource(final Path directory,
                           final Function<String, String> keyMapper,
                           final Predicate<String> filter) {
        if (!Files.exists(directory)) {
            return;
        }
        try (final var child = Files.list(directory)) {
            values.putAll(child
                    .filter(Files::isRegularFile)
                    .map(it -> {
                        try {
                            return entry(it.getFileName().toString(), Files.readString(it, UTF_8));
                        } catch (final IOException ioe) {
                            Logger.getLogger(getClass().getName()).log(Level.FINER, ioe, ioe::getMessage);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(e -> filter.test(e.getKey()))
                    .collect(toMap(e -> keyMapper.apply(e.getKey()), Map.Entry::getValue)));
        } catch (final IOException e) {
            Logger.getLogger(getClass().getName()).log(Level.FINER, e, e::getMessage);
        }
    }

    @Override
    public String get(final String key) {
        return values.get(key);
    }
}
