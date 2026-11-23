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
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@ApplicationScoped
public class JsonRpcNumericParams implements Supplier<List<?>> {
    private List<?> last;

    @Override
    public List<?> get() {
        return last;
    }

    @JsonRpc("roots")
    public void roots(final double d, final Double boxedD, final float f, final Float boxedF, final Number n) {
        last = Arrays.asList(d, boxedD, f, boxedF, n);
    }

    @JsonRpc("collections")
    public void collections(final List<Double> d, final List<Float> f, final List<Number> n,
                            final Set<Double> set, final Collection<Float> collection) {
        last = Arrays.asList(d, f, n, set, collection);
    }

    @JsonRpc("maps")
    public void maps(final Map<String, Double> d, final Map<String, Float> f, final Map<String, Number> n) {
        last = Arrays.asList(d, f, n);
    }

    @JsonRpc("optionals")
    public void optionals(final Optional<Double> d, final Optional<Float> f, final Optional<Number> n) {
        last = Arrays.asList(d, f, n);
    }
}
