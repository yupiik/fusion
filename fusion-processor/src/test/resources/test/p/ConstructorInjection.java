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

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import java.util.List;

@ApplicationScoped // ensure a normal scoped instance works - with subclassing challenge, which means default works too
public class ConstructorInjection {
    private final String value;

    protected ConstructorInjection() { // for proxies/context
        this.value = null;
    }

    public ConstructorInjection(final Bean2 bean2, final List<Bean21> list) {
        this.value = "constructor<bean2=" + bean2 + ",list=" + list + ">";
    }

    @Override
    public String toString() {
        return value;
    }
}
