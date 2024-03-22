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
package io.yupiik.fusion.testing.launcher;

import io.yupiik.fusion.framework.api.ConfiguringContainer;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;

import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FusionCLITestTest {
    @FusionCLITest(args = {"test", "run"}, customizer = DropWebServerForTest.class)
    void run(final Stdout stdout) {
        assertEquals("Args=Args[args=[test, run]]", stdout.content().strip());
    }

    public static class DropWebServerForTest implements FusionCLITest.Customizer {
        @Override
        public ConfiguringContainer apply(final ConfiguringContainer container) {
            return container.register(new FusionModule() {
                @Override
                public BiPredicate<RuntimeContainer, FusionBean<?>> beanFilter() {
                    return (c, b) -> !b.getClass().getName().startsWith("io.yupiik.fusion.http.server.");
                }

                @Override
                public BiPredicate<RuntimeContainer, FusionListener<?>> listenerFilter() {
                    return (c, l) -> !l.getClass().getName().startsWith("io.yupiik.fusion.http.server.");
                }
            });
        }
    }
}
