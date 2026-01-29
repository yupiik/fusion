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
package io.yupiik.fusion.observability.metrics;

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

@ApplicationScoped
public class MetricsRegistry {
    private final Map<String, RegisteredMetric> counters = new ConcurrentHashMap<>();
    private final Map<String, RegisteredMetric> gauges = new ConcurrentHashMap<>();

    public boolean isEmpty() {
        return counters.isEmpty() && gauges.isEmpty();
    }

    public int getStatisticsSize() {
        return counters.size() + gauges.size();
    }

    public Stream<Map.Entry<String, RegisteredMetric>> entries() {
        return Stream.concat(
                counters.entrySet().stream(),
                gauges.entrySet().stream());
    }

    public MetricsRegistry registerReadOnlyGauge(final String name, final String unit, final LongSupplier supplier) {
        if (counters.containsKey(name) || gauges.put(name, new RegisteredMetric(name, "gauge", null, supplier, unit)) != null) {
            throw new IllegalArgumentException("'" + name + "' gauge already registered");
        }
        return this;
    }

    public LongAdder getOrCreateMovingGauge(final String name, final String unit) {
        if (counters.containsKey(name)) {
            throw new IllegalArgumentException("'" + name + "' gauge already registered");
        }
        return requireNonNull(gauges.computeIfAbsent(name, k -> {
            final var longAdder = new LongAdder();
            return new RegisteredMetric(name, "gauge", longAdder, new LongAdderEvaluator(longAdder), unit);
        }).adder(), "Can't use getOrCreateMovingGauge on a read only gauge");
    }

    public LongAdder registerCounter(final String name, final String unit) {
        if (gauges.containsKey(name)) {
            throw new IllegalArgumentException("'" + name + "' counter already registered");
        }
        final var adder = new LongAdder();
        if (counters.put(name, new RegisteredMetric(name, "counter", adder, new LongAdderEvaluator(adder), unit)) != null) {
            throw new IllegalArgumentException("'" + name + "' counter already registered");
        }
        return adder;
    }

    public void unregisterCounter(final String name) {
        counters.remove(name);
    }

    public void unregisterGauge(final String name) {
        gauges.remove(name);
    }

    private static class LongAdderEvaluator implements LongSupplier, Supplier<LongAdder> {
        private final LongAdder delegate;

        private LongAdderEvaluator(final LongAdder adder) {
            this.delegate = adder;
        }

        @Override
        public long getAsLong() {
            return delegate.sum();
        }

        @Override
        public LongAdder get() {
            return delegate;
        }
    }

    public record RegisteredMetric(LongAdder adder, LongSupplier supplier, String typeLine, String unitLine) {
        private RegisteredMetric(final String name,
                                 final String type,
                                 final LongAdder adder,
                                 final LongSupplier supplier,
                                 final String unit) {
            this(adder, supplier,
                    "# TYPE " + getMetaName(name) + " " + type,
                    unit == null ? null : "# UNIT " + getMetaName(name) + " " + unit);
        }

        private static String getMetaName(final String name) {
            final int tagStart = name.indexOf('{');
            return tagStart > 0 ? name.substring(0, tagStart) : name;
        }
    }
}
