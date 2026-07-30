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
package io.yupiik.fusion.json.benchmarks;

import tools.jackson.databind.ObjectMapper;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.benchmarks.model.Flat;
import io.yupiik.fusion.json.benchmarks.model.Flat$FusionJsonCodec;
import io.yupiik.fusion.json.benchmarks.model.Metrics;
import io.yupiik.fusion.json.benchmarks.model.Metrics$FusionJsonCodec;
import io.yupiik.fusion.json.benchmarks.model.Nested;
import io.yupiik.fusion.json.benchmarks.model.Nested$FusionJsonCodec;
import io.yupiik.fusion.json.benchmarks.model.WithList;
import io.yupiik.fusion.json.benchmarks.model.WithList$FusionJsonCodec;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class RecordRoundTripBenchmark {
    private JsonMapper fusion;
    private ObjectMapper jackson;

    private Flat flat;
    private Nested nested;
    private WithList withList;
    private Metrics metrics;

    private String flatJson;
    private String nestedJson;
    private String withListJson;
    private String metricsJson;

    private byte[] flatBytes;
    private byte[] nestedBytes;
    private byte[] withListBytes;
    private byte[] metricsBytes;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        fusion = new JsonMapperImpl(
                List.of(new Flat$FusionJsonCodec(), new Nested$FusionJsonCodec(), new WithList$FusionJsonCodec(),
                        new Metrics$FusionJsonCodec()),
                key -> Optional.empty());
        jackson = new ObjectMapper();

        flat = new Flat("service-a", "Paris", "France", 42, 123456789L, true);
        nested = new Nested("id-1", "some label", flat);
        withList = new WithList("id-2", IntStream.range(0, 10)
                .mapToObj(i -> new Flat("service-" + i, "City" + i, "Country" + i, 20 + i, 1000L + i, i % 2 == 0))
                .toList());
        metrics = new Metrics("metrics-1", 0.001, 1234.5678, 42.55, 12.5, 99.99, 123.456);

        flatJson = jackson.writeValueAsString(flat);
        nestedJson = jackson.writeValueAsString(nested);
        withListJson = jackson.writeValueAsString(withList);
        metricsJson = jackson.writeValueAsString(metrics);

        // sanity: both mappers must agree on the payloads before we compare anything
        ensure(flat, fusion.fromString(Flat.class, flatJson));
        ensure(nested, fusion.fromString(Nested.class, nestedJson));
        ensure(withList, fusion.fromString(WithList.class, withListJson));
        ensure(metrics, fusion.fromString(Metrics.class, metricsJson));
        ensure(flat, jackson.readValue(fusion.toString(flat), Flat.class));
        ensure(nested, jackson.readValue(fusion.toString(nested), Nested.class));
        ensure(withList, jackson.readValue(fusion.toString(withList), WithList.class));
        ensure(metrics, jackson.readValue(fusion.toString(metrics), Metrics.class));

        flatBytes = flatJson.getBytes(StandardCharsets.UTF_8);
        nestedBytes = nestedJson.getBytes(StandardCharsets.UTF_8);
        withListBytes = withListJson.getBytes(StandardCharsets.UTF_8);
        metricsBytes = metricsJson.getBytes(StandardCharsets.UTF_8);
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        fusion.close();
    }

    private void ensure(final Object expected, final Object actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("mappers disagree:\n" + expected + "\n" + actual);
        }
    }

    // --- flat record

    @Benchmark
    public Flat fusionFromStringFlat() {
        return fusion.fromString(Flat.class, flatJson);
    }

    @Benchmark
    public Flat jacksonFromStringFlat() throws Exception {
        return jackson.readValue(flatJson, Flat.class);
    }

    @Benchmark
    public Flat fusionFromBytesFlat() {
        return fusion.fromBytes(Flat.class, flatBytes);
    }

    @Benchmark
    public Flat jacksonFromBytesFlat() throws Exception {
        return jackson.readValue(flatBytes, Flat.class);
    }

    @Benchmark
    public String fusionToStringFlat() {
        return fusion.toString(flat);
    }

    @Benchmark
    public String jacksonToStringFlat() throws Exception {
        return jackson.writeValueAsString(flat);
    }

    @Benchmark
    public byte[] fusionToBytesFlat() {
        return fusion.toBytes(flat);
    }

    @Benchmark
    public byte[] jacksonToBytesFlat() throws Exception {
        return jackson.writeValueAsBytes(flat);
    }

    // --- double heavy record (reads exercise the parser double fast path)

    @Benchmark
    public Metrics fusionFromStringMetrics() {
        return fusion.fromString(Metrics.class, metricsJson);
    }

    @Benchmark
    public Metrics jacksonFromStringMetrics() throws Exception {
        return jackson.readValue(metricsJson, Metrics.class);
    }

    @Benchmark
    public Metrics fusionFromBytesMetrics() {
        return fusion.fromBytes(Metrics.class, metricsBytes);
    }

    @Benchmark
    public Metrics jacksonFromBytesMetrics() throws Exception {
        return jackson.readValue(metricsBytes, Metrics.class);
    }

    // --- nested record

    @Benchmark
    public Nested fusionFromStringNested() {
        return fusion.fromString(Nested.class, nestedJson);
    }

    @Benchmark
    public Nested jacksonFromStringNested() throws Exception {
        return jackson.readValue(nestedJson, Nested.class);
    }

    @Benchmark
    public Nested fusionFromBytesNested() {
        return fusion.fromBytes(Nested.class, nestedBytes);
    }

    @Benchmark
    public Nested jacksonFromBytesNested() throws Exception {
        return jackson.readValue(nestedBytes, Nested.class);
    }

    @Benchmark
    public String fusionToStringNested() {
        return fusion.toString(nested);
    }

    @Benchmark
    public String jacksonToStringNested() throws Exception {
        return jackson.writeValueAsString(nested);
    }

    @Benchmark
    public byte[] fusionToBytesNested() {
        return fusion.toBytes(nested);
    }

    @Benchmark
    public byte[] jacksonToBytesNested() throws Exception {
        return jackson.writeValueAsBytes(nested);
    }

    // --- record with a list of records

    @Benchmark
    public WithList fusionFromStringWithList() {
        return fusion.fromString(WithList.class, withListJson);
    }

    @Benchmark
    public WithList jacksonFromStringWithList() throws Exception {
        return jackson.readValue(withListJson, WithList.class);
    }

    @Benchmark
    public WithList fusionFromBytesWithList() {
        return fusion.fromBytes(WithList.class, withListBytes);
    }

    @Benchmark
    public WithList jacksonFromBytesWithList() throws Exception {
        return jackson.readValue(withListBytes, WithList.class);
    }

    @Benchmark
    public String fusionToStringWithList() {
        return fusion.toString(withList);
    }

    @Benchmark
    public String jacksonToStringWithList() throws Exception {
        return jackson.writeValueAsString(withList);
    }

    @Benchmark
    public byte[] fusionToBytesWithList() {
        return fusion.toBytes(withList);
    }

    @Benchmark
    public byte[] jacksonToBytesWithList() throws Exception {
        return jackson.writeValueAsBytes(withList);
    }
}
