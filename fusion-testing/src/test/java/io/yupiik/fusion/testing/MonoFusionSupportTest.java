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

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

class MonoFusionSupportTest {
    @Test
    void containerIsSharedByAllTestClasses() {
        final var summary = new SummaryGeneratingListener();
        Tracker.launched.set(true);
        try {
            LauncherFactory.create().execute(
                    request()
                            // run sequentially: the container must survive the end of the first class
                            .selectors(selectClass(FirstClass.class), selectClass(SecondClass.class))
                            .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                            .build(),
                    summary);
        } finally {
            Tracker.launched.set(false);
        }

        final var result = summary.getSummary();
        final var failures = new StringWriter();
        result.printFailuresTo(new PrintWriter(failures), 20);
        assertEquals(2, result.getTestsSucceededCount(), failures::toString);
    }

    @MonoFusionSupport
    static class FirstClass {
        @Test
        void lookup(@Fusion final Tracker tracker) {
            assumeTrue(Tracker.launched.get(), "only run by containerIsSharedByAllTestClasses");
            Tracker.first.set(tracker.self());
        }
    }

    @MonoFusionSupport
    static class SecondClass {
        @Test
        void lookup(@Fusion final Tracker tracker) {
            assumeTrue(Tracker.launched.get(), "only run by containerIsSharedByAllTestClasses");
            assertEquals(0, Tracker.destroyed.get(), "the shared container must not be closed between test classes");
            assertSame(Tracker.first.get(), tracker.self());
        }
    }

    public static class Tracker {
        private static final AtomicBoolean launched = new AtomicBoolean(); // not run when discovered alone (IDE)
        private static final AtomicReference<Tracker> first = new AtomicReference<>();
        private static final AtomicInteger destroyed = new AtomicInteger();

        private Tracker self() {
            return this;
        }
    }

    public static class Module implements FusionModule {
        @Override
        public Stream<FusionBean<?>> beans() {
            return Stream.of(new BaseBean<Tracker>(Tracker.class, ApplicationScoped.class, 1000, Map.of()) {
                @Override
                public Tracker create(final RuntimeContainer container, final List<Instance<?>> dependents) {
                    return new Tracker();
                }

                @Override
                public void destroy(final RuntimeContainer container, final Tracker instance) {
                    Tracker.destroyed.incrementAndGet();
                }
            });
        }
    }
}
