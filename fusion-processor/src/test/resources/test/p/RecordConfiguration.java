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

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;

import java.math.BigDecimal;
import java.util.List;

@RootConfiguration("app")
public record RecordConfiguration(
        @Property(documentation = "The app name") String name,
        boolean toggle,
        int age,
        @Property("bigInt") long aLong,
        double number,
        BigDecimal bigNumber,
        NestedConf nested,
        List<NestedConf> nesteds,
        List<String> list,
        EnumType type,
        @Property(defaultValue = "100") Integer intWithDefault,
        @Property(defaultValue = "\"bump\"") String strWithDefault,
        @Property(defaultValue = "java.util.List.of(\"bump\",\"bump2\")") List<String> listStrWithDefault) {
}
