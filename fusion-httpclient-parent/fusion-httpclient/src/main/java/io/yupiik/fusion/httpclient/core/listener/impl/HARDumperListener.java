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
package io.yupiik.fusion.httpclient.core.listener.impl;

import io.yupiik.fusion.httpclient.core.listener.impl.har.HarJsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Usage:
 * <p>
 * {@code new HARDumperListener(
 * Paths.get(clientConfiguration.getHarDump()),
 * Clock.systemUTC(),
 * new HARDumperListener.Har(),
 * Logger.getLogger(getClass().getName()))}
 */
public class HARDumperListener extends BaseHARDumperListener implements AutoCloseable {
    private final Har har = new Har(new Har.Log("1.2", null, null, null, new CopyOnWriteArrayList<>(), ""));

    public HARDumperListener(final Path output, final Clock clock, final Logger logger) {
        this(new Configuration(output, clock, logger));
    }

    public HARDumperListener(final Configuration configuration) {
        super(configuration);
    }

    @Override
    public void close() throws Exception {
        if (configuration.output.getParent() != null) {
            Files.createDirectories(configuration.output.getParent());
        }

        try (final var writer = Files.newBufferedWriter(configuration.output);
             final var mapper = new HarJsonMapper()) {
            mapper.write(har, writer);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
        configuration.logger.info(() -> "Dumped HAR to '" + configuration.output + "'");
    }

    public Har getHar() {
        return har;
    }

    @Override
    protected void onEntry(final Har.Entry entry) {
        har.log().entries().add(entry);
    }

    public static class Configuration extends BaseConfiguration<Configuration> {
        public Configuration(final Path output, final Clock clock, final Logger logger) {
            super(output, clock, logger);
        }
    }
}
