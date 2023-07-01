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
package io.yupiik.fusion.persistence.impl;

import io.yupiik.fusion.persistence.impl.translation.DefaultTranslation;
import io.yupiik.fusion.persistence.spi.DatabaseTranslation;

import java.util.function.Function;

public class BaseDatabaseConfiguration<A> {
    protected Function<Class<?>, Object> instanceLookup;
    protected DatabaseTranslation translation;

    public Function<Class<?>, Object> getInstanceLookup() {
        return instanceLookup;
    }

    @SuppressWarnings("unchecked")
    public A setInstanceLookup(final Function<Class<?>, Object> instanceLookup) {
        this.instanceLookup = instanceLookup;
        return (A) this;
    }

    public DatabaseTranslation getTranslation() {
        if (translation == null) {
            translation = new DefaultTranslation();
        }
        return translation;
    }

    @SuppressWarnings("unchecked")
    public A setTranslation(final DatabaseTranslation translation) {
        this.translation = translation;
        return (A) this;
    }

    /**
     * @return {@code this} instance, mainly used to ensure it can be passed to a
     * {@link io.yupiik.fusion.persistence.api.Database} instance.
     */
    @SuppressWarnings("unchecked")
    public A validate() {
        // nothing required as of today
        return (A) this;
    }
}
