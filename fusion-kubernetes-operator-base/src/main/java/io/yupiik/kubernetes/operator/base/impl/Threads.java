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
package io.yupiik.kubernetes.operator.base.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MINUTES;

public record Threads(ScheduledExecutorService executor) implements AutoCloseable {
    @Override
    public void close() throws Exception {
        executor.shutdownNow().forEach(Runnable::run);
        if (!executor.awaitTermination(1, MINUTES)) {
            Logger.getLogger(getClass().getName()).warning("Can't stop thread pool in 1mn, giving up");
        }
    }
}
