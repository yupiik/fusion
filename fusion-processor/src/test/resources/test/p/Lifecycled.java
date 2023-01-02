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

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.framework.build.api.lifecycle.Init;
import io.yupiik.fusion.framework.build.api.scanning.Injection;

@ApplicationScoped
public class Lifecycled extends LifecycledDep {
    @Injection
    LifecycledDep bean2;

    @Init
    @Override
    protected void init() {
        if (bean2 != null) {
            super.init();
        }
    }

    @Destroy
    @Override
    protected void destroy() {
        if (bean2 != null) {
            super.destroy();
        }
    }

    @Override
    public String toString() {
        return super.toString() + ", dep[" + bean2 + "]";
    }
}
