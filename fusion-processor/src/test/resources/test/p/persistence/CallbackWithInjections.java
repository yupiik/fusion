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
package test.p.persistence;

import io.yupiik.fusion.framework.build.api.persistence.OnDelete;
import io.yupiik.fusion.framework.build.api.persistence.OnUpdate;

import test.p.Bean2;
import io.yupiik.fusion.framework.build.api.persistence.Column;
import io.yupiik.fusion.framework.build.api.persistence.Id;
import io.yupiik.fusion.framework.build.api.persistence.OnInsert;
import io.yupiik.fusion.framework.build.api.persistence.OnLoad;
import io.yupiik.fusion.framework.build.api.persistence.Table;

import static java.util.Objects.requireNonNull;

@Table("CB_INJECTION")
public record CallbackWithInjections(@Id String id) {
    @OnInsert
    public CallbackWithInjections onInsert(final Bean2 bean) {
        assertNotNull(bean);
        return this;
    }

    @OnLoad
    public CallbackWithInjections onLoad(final Bean2 bean) {
        assertNotNull(bean);
        return this;
    }

    @OnUpdate
    public CallbackWithInjections onUpdate(final Bean2 bean) {
        assertNotNull(bean);
        return this;
    }

    @OnDelete
    public void onDelete(final Bean2 bean) {
        assertNotNull(bean);
    }

    private void assertNotNull(final Object value) {
        requireNonNull(value, "injection is null");
    }
}