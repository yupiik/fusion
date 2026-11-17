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

import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.lifecycle.Stop;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

class FusionSupportLifecycleTest {
    private static final AtomicBoolean launched = new AtomicBoolean(); // nested class is not run when discovered alone (IDE)
    private static final AtomicInteger stops = new AtomicInteger();

    @Test
    void stopIsFiredOnce() {
        final var summary = new SummaryGeneratingListener();
        launched.set(true);
        try {
            LauncherFactory.create().execute(request().selectors(selectClass(PerClass.class)).build(), summary);
        } finally {
            launched.set(false);
        }

        final var failures = new StringWriter();
        summary.getSummary().printFailuresTo(new PrintWriter(failures), 20);
        assertEquals(1, summary.getSummary().getTestsSucceededCount(), failures::toString);
        assertEquals(1, stops.get());
    }

    @FusionSupport(modules = StopCounter.class, disableDiscovery = true)
    static class PerClass {
        @Test
        void run() {
            assumeTrue(launched.get(), "only run by stopIsFiredOnce");
        }
    }

    public static class StopCounter implements FusionModule {
        @Override
        public Stream<FusionListener<?>> listeners() {
            return Stream.of(new FusionListener<Stop>() {
                @Override
                public Class<Stop> eventType() {
                    return Stop.class;
                }

                @Override
                public void onEvent(final RuntimeContainer container, final Stop event) {
                    if (launched.get()) {
                        stops.incrementAndGet();
                    }
                }
            });
        }
    }
}
