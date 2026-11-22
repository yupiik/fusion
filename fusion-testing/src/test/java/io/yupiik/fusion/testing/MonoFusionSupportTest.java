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
package io.yupiik.fusion.testing;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

class MonoFusionSupportTest {
    private static final AtomicBoolean launched = new AtomicBoolean(); // nested classes are not run when discovered alone (IDE)
    private static final AtomicReference<Configuration> first = new AtomicReference<>();

    @Test
    void containerIsSharedByAllTestClasses() {
        final var summary = new SummaryGeneratingListener();
        launched.set(true);
        try {
            LauncherFactory.create().execute(
                    request()
                            // run sequentially: the container must survive the end of the first class
                            .selectors(selectClass(FirstClass.class), selectClass(SecondClass.class))
                            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                            .build(),
                    summary);
        } finally {
            launched.set(false);
        }

        final var result = summary.getSummary();
        final var failures = new StringWriter();
        result.printFailuresTo(new PrintWriter(failures), 20);
        assertEquals(2, result.getTestsSucceededCount(), failures::toString);
    }

    @MonoFusionSupport
    static class FirstClass {
        @Test
        void lookup(@Fusion final Configuration configuration) {
            assumeTrue(launched.get(), "only run by containerIsSharedByAllTestClasses");
            first.set(configuration);
        }
    }

    @MonoFusionSupport
    static class SecondClass {
        @Test
        void lookup(@Fusion final Configuration configuration) {
            assumeTrue(launched.get(), "only run by containerIsSharedByAllTestClasses");
            // application scoped: a closed container would have destroyed it and the lookup would create a new one
            assertSame(first.get(), configuration, "the shared container must not be closed between test classes");
        }
    }
}
