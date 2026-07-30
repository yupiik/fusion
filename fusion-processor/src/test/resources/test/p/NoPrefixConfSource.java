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

@Bean
public class NoPrefixConfSource implements ConfigurationSource {
    private final Map<String, String> data = Map.of(
            "name", "test",
            "port", "8080",
            "nested.lower", "down",
            "list", "ab,cde");

    @Override
    public String get(final String key) {
        return data.get(key);
    }
}
