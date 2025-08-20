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

import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc;
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpcParam;

import java.util.function.Supplier;

public class JsonRpcDoubleParam implements Supplier<String> {
    public static String last = null;

    @Override
    public String get() {
        return last;
    }

    @JsonRpc("param")
    public void call(@JsonRpcParam final double par, @JsonRpcParam final Double par2) {
        // just to avoid common errors with toString() over jvm versions we do not care about
        last = par == 1.2 && par2 == 1.2 ? "1.2" : "-1";
    }
}
