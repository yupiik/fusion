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

package io.yupiik.fusion.framework.processor.internal;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Objects;
import java.util.Set;

public class Bean {
    private final Element enclosing;
    private final String name;
    private final int hash;

    public Bean(final Element enclosing, final String name) {
        this.enclosing = enclosing;
        this.name = name;

        this.hash = Objects.hash(name);
    }

    public Element enclosing() {
        return enclosing;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return name.equals(((Bean) o).name);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public record FieldInjection(String name, TypeMirror type,
                                 boolean list, boolean set, boolean optional,
                                 Set<Modifier> modifiers) {
    }
}