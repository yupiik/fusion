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
package io.yupiik.fusion.persistence.impl.bean;

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionModule;

import java.util.stream.Stream;

public class PersistenceModule implements FusionModule {
    @Override
    public Stream<FusionBean<?>> beans() {
        final var optionals = optionalBeans();
        return Stream.concat(optionals, Stream.of(
                new FusionDatabaseConfigurationBean(),
                new FusionDatabaseBean(),
                new FusionDatabaseFactoryBean()));
    }

    private Stream<FusionBean<?>> optionalBeans() { // only if tomcat-jdbc is in the classpath
        try {
            return Stream.of(new FusionDataSourceBean());
        } catch (final NoClassDefFoundError | RuntimeException ncdef) {
            return Stream.empty();
        }
    }
}
