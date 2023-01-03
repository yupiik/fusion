/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
import io.yupiik.fusion.json.JsonMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.logging.Logger;

/**
 * Intent of this class is to have a more streaming or high volume friendly dumper than fully in memory one (HAR flavor).
 * It reuses the same model but dumps one entry per line.
 */
public class NDJSONDumperListener extends BaseHARDumperListener implements AutoCloseable {
    private final BufferedWriter stream;
    private final JsonMapper mapper = new HarJsonMapper();
    private final boolean flush;

    public NDJSONDumperListener(final Configuration configuration) {
        super(configuration);
        this.flush = configuration.flushOnEachEntry;

        if (configuration.output.getParent() != null) {
            try {
                Files.createDirectories(configuration.output.getParent());
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        try {
            stream = Files.newBufferedWriter(configuration.output);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() throws Exception {
        stream.close();
        configuration.logger.info(() -> "Dumped ND-JSON to '" + configuration.output + "'");
    }

    @Override
    protected synchronized void onEntry(final Har.Entry entry) {
        try {
            stream.write(toJson(entry) + '\n');
            if (flush) {
                stream.flush();
            }
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private String toJson(final Har.Entry entry) {
        return mapper.toString(entry);
    }

    public static class Configuration extends BaseConfiguration<Configuration> {
        private boolean flushOnEachEntry = true;

        public Configuration(final Path output, final Clock clock, final Logger logger) {
            super(output, clock, logger);
        }

        public Configuration setFlushOnEachEntry(final boolean flushOnEachEntry) {
            this.flushOnEachEntry = flushOnEachEntry;
            return this;
        }
    }
}
