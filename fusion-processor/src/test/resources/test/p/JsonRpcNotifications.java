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

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class JsonRpcNotifications implements Supplier<String> {
    public static String last = null;

    @Override
    public String get() {
        return last;
    }

    @JsonRpc("notification1")
    public void result() {
        last = "notification1";
    }

    @JsonRpc("notification2")
    public CompletionStage<Void> asynResult() {
        last = "notification2";
        return completedFuture(null);
    }
}
