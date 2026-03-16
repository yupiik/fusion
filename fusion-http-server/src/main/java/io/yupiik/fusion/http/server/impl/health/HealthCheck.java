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
package io.yupiik.fusion.http.server.impl.health;

import java.util.concurrent.CompletionStage;

public interface HealthCheck {
    /**
     * @return identifier for this healthcheck.
     */
    String name();

    /**
     * @return type to match when calling {@code /health?type=xxxx} endpoint.
     */
    default String type() {
        return "live";
    }

    /**
     * IMPORTANT: ensure to implement some consistent timeouts and error handling if you deploy in kubernetes to avoid to hang any healthcheck or require fusion to cancel them.
     *
     * @return the implementation of the check itself, key point is to return the status in the result.
     */
    CompletionStage<Result> check();

    record Result(Status status, String message) {
    }

    enum Status {
        OK, KO
    }
}
