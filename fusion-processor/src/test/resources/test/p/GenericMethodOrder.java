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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@ApplicationScoped
public class GenericMethodOrder {
    // the return type only uses the second declared type parameter,
    // the generated subclass must keep the declaration order <A, T>
    public <A, T> CompletionStage<T> forwardWithFallback(final A input, final Function<A, T> mapper) {
        return CompletableFuture.completedFuture(mapper.apply(input));
    }

    // parameterized bound, must be preserved in the generated override
    public <A extends Comparable<A>, T> List<T> bounded(final A value, final Function<A, T> mapper) {
        return List.of(mapper.apply(value));
    }
}
