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
package test.p;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.scanning.Bean;
import io.yupiik.fusion.framework.build.api.scanning.Injection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@ApplicationScoped
public class MethodProducer {
    @Injection
    Bean2 bean2;

    private static final List<String> EVENTS = new ArrayList<>();

    @Bean
    public Produceable create() {
        EVENTS.add("create");
        return new Produceable() {
            @Override
            public void close() throws Exception {
                EVENTS.add("create.close");
            }

            @Override
            public String get() {
                EVENTS.add("create.get(" + bean2 + ")");
                return bean2.toString();
            }
        };
    }

    @Bean
    public static String globalConf() {
        EVENTS.add("globalConf");
        return "<conf>";
    }

    @Override
    public String toString() {
        return String.join(", ", EVENTS);
    }

    public interface Produceable extends Supplier<String>, AutoCloseable {
    }
}