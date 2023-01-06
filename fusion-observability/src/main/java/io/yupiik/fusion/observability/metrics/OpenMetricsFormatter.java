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
package io.yupiik.fusion.observability.metrics;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class OpenMetricsFormatter implements Function<Stream<Map.Entry<String, MetricsRegistry.RegisteredMetric>>, String> {
    @Override
    public String apply(final Stream<Map.Entry<String, MetricsRegistry.RegisteredMetric>> entries) {
        return entries
                .sorted(Map.Entry.comparingByKey())
                .flatMap(it -> toOpenMetrics(it.getKey(), it.getValue()))
                .collect(joining("\n", "", "\n# EOF"));
    }

    private Stream<String> toOpenMetrics(final String name, final MetricsRegistry.RegisteredMetric metric) {
        return Stream.of(
                        metric.typeLine(),
                        metric.unitLine(),
                        name + " " + metric.supplier().getAsLong())
                .filter(Objects::nonNull);
    }
}
