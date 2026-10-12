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
package test.p;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.framework.build.api.scanning.Bean;

@ApplicationScoped
public class MethodDestroyProducerWithInjections {
    @Bean
    @Destroy(order = -7)
    public Wrapper create(final String prefix) {
        return new Wrapper(prefix);
    }

    @Bean
    public static String prefix() {
        return "p";
    }

    public static class Wrapper implements AutoCloseable {
        private final String prefix;

        public Wrapper(final String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void close() {
            DestroyRecording.ORDER.add("producer:" + prefix);
        }
    }
}