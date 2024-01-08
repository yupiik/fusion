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
package io.yupiik.fusion.observability.http.test;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.observability.health.HealthCheck;

import java.util.concurrent.CompletionStage;

import static io.yupiik.fusion.observability.health.HealthCheck.Status.OK;
import static java.util.concurrent.CompletableFuture.completedFuture;

@ApplicationScoped
public class SampleCheck implements HealthCheck {
    private CompletionStage<Result> check = completedFuture(new Result(OK, "worked"));

    public void setCheck(final CompletionStage<Result> check) {
        this.check = check;
    }

    @Override
    public String name() {
        return "test-check";
    }

    @Override
    public CompletionStage<Result> check() {
        return check;
    }
}
