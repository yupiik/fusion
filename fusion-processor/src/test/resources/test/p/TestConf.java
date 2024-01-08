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

import io.yupiik.fusion.framework.api.configuration.ConfigurationSource;
import io.yupiik.fusion.framework.build.api.scanning.Bean;

import java.util.Map;

import static java.util.Map.entry;

@Bean
public class TestConf implements ConfigurationSource {
    private final Map<String, String> data = Map.ofEntries(
            entry("app.name", "test"),
            entry("app.toggle", "true"),
            entry("app.age", "123"),
            entry("app.bigInt", "456"),
            entry("app.number", "7.89"),
            entry("app.bigNumber", "10.2"),
            entry("app.list", "ab,cde,fgh"),
            entry("app.type", "ENUM_1"),
            entry("app.nested.nestedValue", "down"),
            entry("app.nested.second.value", "5"),
            entry("app.nesteds.length", "2"),
            entry("app.nesteds.0.nestedValue", "down1"),
            entry("app.nesteds.1.nestedValue", "down2")
    );

    @Override
    public String get(final String key) {
        return data.get(key);
    }
}
