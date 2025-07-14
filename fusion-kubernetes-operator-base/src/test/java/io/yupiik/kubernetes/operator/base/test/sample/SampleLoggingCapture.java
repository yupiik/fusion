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
package io.yupiik.kubernetes.operator.base.test.sample;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SampleLoggingCapture implements AutoCloseable {
    private final Logger logger;
    private final List<String> captured;
    private final Handler handler;
    private final CountDownLatch latch;

    public SampleLoggingCapture(final int awaited, final Predicate<String> filter) {
        logger = Logger.getLogger(SampleOperator.class.getName());
        captured = new CopyOnWriteArrayList<>();
        latch = new CountDownLatch(awaited);
        handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                if (filter.test(record.getMessage())) {
                    captured.add(record.getMessage());
                    latch.countDown();
                }
            }

            @Override
            public void flush() {
                // no-op
            }

            @Override
            public void close() throws SecurityException {
                flush();
            }
        };
        logger.addHandler(handler);
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
    }

    public List<String> all() {
        try {
            assertTrue(latch.await(1, MINUTES));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return captured;
    }
}
