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
package io.yupiik.fusion.framework.build.api.metadata.spi;

/**
 * Service provider API to provide an alias to {@link io.yupiik.fusion.framework.build.api.metadata.BeanMetadata}.
 * It enables to make its API specific.
 */
public interface MetadataContributor {
    /**
     * @return default name.
     */
    String name();

    /**
     * @return default value.
     */
    String value();

    /**
     * @return fully qualified name of the custom annotation decorated with {@link io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias}.
     */
    String annotationType();
}
