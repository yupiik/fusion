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

import io.yupiik.fusion.framework.build.api.persistence.Column;
import io.yupiik.fusion.framework.build.api.persistence.Id;
import io.yupiik.fusion.framework.build.api.persistence.OnInsert;
import io.yupiik.fusion.framework.build.api.persistence.OnLoad;
import io.yupiik.fusion.framework.build.api.persistence.Table;

@Table("SIMPLE_FLAT_ENTITY")
public record SimpleFlatEntity(
        @Id String id,
        String name,
        byte[] arr,
        @Column(name = "SIMPLE_AGE") int age,
        Kind kind) {
    @OnInsert
    public SimpleFlatEntity onInsert() {
        return id() == null ?
                new SimpleFlatEntity(name(), name(), null, 1, kind()) :
                this;
    }

    @OnLoad
    public SimpleFlatEntity onLoad() {
        return "loaded".equals(name) ?
                new SimpleFlatEntity(id() == null ? name() : id(), name(), null, 1, kind()) :
                this;
    }

    public enum Kind {
        SIMPLE
    }
}