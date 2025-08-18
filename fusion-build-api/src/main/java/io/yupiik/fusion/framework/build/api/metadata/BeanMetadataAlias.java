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
package io.yupiik.fusion.framework.build.api.metadata;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Marks an annotation which will behave exactly as {@link BeanMetadata} but hardcoding
 * either name or value or both.
 * If you want to enable end user to override one of both just redefine the method in your custom annotation.
 * <p>
 * This enables to expose a more specialized API.
 * <p>
 * Concretely it will generate a {@link io.yupiik.fusion.framework.build.api.metadata.spi.MetadataContributor}.
 * It is recommended to make it a build time only module (scope provided) but not required.
 * It is only consumed by the annotation processor (fusion-processor) and not the runtime.
 * <p>
 * Common examples are:
 * <ul>
 *     <li>Always <code>true</code> value</li>
 *     <li>Fixed name to have a {@code @MyName(value)} API</li>
 * </ul>
 */
@Retention(SOURCE)
@Target(ANNOTATION_TYPE)
public @interface BeanMetadataAlias {
    String UNSET = "__io.yupiik.fusion.framework.build.api.metadata.BeanMetadataAlias__";

    /**
     * @return the default name of the metadata if {@code name()} is not defined in the decorated annotation.
     */
    String name() default UNSET;

    /**
     * @return the default value of the metadata if {@code name()} is not defined in the decorated annotation.
     */
    String value() default UNSET;
}
