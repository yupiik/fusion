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
package io.yupiik.kubernetes.operator.base.spi;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.kubernetes.operator.base.impl.ObjectLike;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;

public interface Operator<T extends ObjectLike> {
    default void onAdd(final T resource) {
        // no-op
    }

    default void onModify(final T resource) {
        onDelete(resource);
        onAdd(resource);
    }

    default void onDelete(final T resource) {
        // no-op
    }

    default CompletionStage<?> onStart() {
        return completedFuture(true);
    }

    default void onStop() {
        // no-op
    }

    /**
     * @return resource model type.
     */
    Class<T> resourceType();

    /**
     * @return resource name to watch (plural form).
     */
    String pluralName();

    /**
     * @return the apiversion to use to query and watch the resources.
     */
    String apiVersion();

    /**
     * @return {@link true} if querying should use {@link #namespaces()} or global querying.
     */
    default boolean namespaced() {
        return true;
    }

    default List<String> namespaces() {
        return List.of();
    }

    class Base<T extends ObjectLike> implements Operator<T> {
        private final Class<T> resourceType;
        private final DefaultOperatorConfiguration configuration;

        public Base(final Class<T> resourceType, final DefaultOperatorConfiguration configuration) {
            this.resourceType = resourceType;
            this.configuration = configuration;
        }

        @Override
        public Class<T> resourceType() {
            return resourceType;
        }

        @Override
        public String pluralName() {
            return configuration.pluralResource();
        }

        @Override
        public String apiVersion() {
            return configuration.apiVersion();
        }

        @Override
        public boolean namespaced() {
            return configuration.namespaced();
        }

        @Override
        public List<String> namespaces() {
            return configuration.namespaces();
        }
    }

    /**
     * Reusable configuration in a custom {@link io.yupiik.fusion.framework.build.api.configuration.RootConfiguration}.
     */
    record DefaultOperatorConfiguration(
            @Property(defaultValue = "true",
                    documentation = "Is this operator handling resources per namespaces using the `namespaces` explicit list.")
            boolean namespaced,

            @Property(defaultValue = "java.util.List.of(\"default\")",
                    documentation = "Namespaces to watch (assumes a namespaced operator).")
            List<String> namespaces,

            @Property(value = "resource-plural-name", required = true, documentation = "Plural name of the resources to manage.")
            String pluralResource,

            @Property(value = "api-version", required = true, documentation = "API version to watch.")
            String apiVersion) {
    }
}
