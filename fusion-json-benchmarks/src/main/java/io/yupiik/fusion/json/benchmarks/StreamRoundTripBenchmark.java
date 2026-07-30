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

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.benchmarks.model.Flat;
import io.yupiik.fusion.json.benchmarks.model.Flat$FusionJsonCodec;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * Stream oriented comparison: fusion only exposes Reader/Writer (bridged from byte streams
 * the way a caller would do it today) while jackson has native InputStream/OutputStream support.
 */
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class StreamRoundTripBenchmark {
    private JsonMapper fusion;
    private tools.jackson.databind.ObjectMapper jackson;

    private Flat flat;
    private WithList withList;

    private byte[] flatBytes;
    private byte[] withListBytes;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        fusion = new JsonMapperImpl(
                List.of(new Flat$FusionJsonCodec(), new WithList$FusionJsonCodec()),
                key -> Optional.empty());
        jackson = new tools.jackson.databind.ObjectMapper();

        flat = new Flat("service-a", "Paris", "France", 42, 123456789L, true);
        withList = new WithList("id-2", IntStream.range(0, 10)
                .mapToObj(i -> new Flat("service-" + i, "City" + i, "Country" + i, 20 + i, 1000L + i, i % 2 == 0))
                .toList());

        flatBytes = jackson.writeValueAsBytes(flat);
        withListBytes = jackson.writeValueAsBytes(withList);

        // sanity
        if (!flat.equals(fusion.read(Flat.class, new InputStreamReader(new ByteArrayInputStream(flatBytes), StandardCharsets.UTF_8)))
                || !withList.equals(fusion.read(WithList.class, new InputStreamReader(new ByteArrayInputStream(withListBytes), StandardCharsets.UTF_8)))) {
            throw new IllegalStateException("mappers disagree");
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        fusion.close();
    }

    // --- read from a byte stream

    @Benchmark
    public Flat fusionReadStreamFlat() {
        return fusion.read(Flat.class, new InputStreamReader(new ByteArrayInputStream(flatBytes), StandardCharsets.UTF_8));
    }

    @Benchmark
    public Flat fusionReadByteStreamFlat() { // the byte-stream API (FastUtf8Reader)
        return fusion.read(Flat.class, new ByteArrayInputStream(flatBytes));
    }

    @Benchmark
    public WithList fusionReadByteStreamWithList() {
        return fusion.read(WithList.class, new ByteArrayInputStream(withListBytes));
    }

    @Benchmark
    public Flat jacksonReadStreamFlat() {
        return jackson.readValue(new ByteArrayInputStream(flatBytes), Flat.class);
    }

    @Benchmark
    public WithList fusionReadStreamWithList() {
        return fusion.read(WithList.class, new InputStreamReader(new ByteArrayInputStream(withListBytes), StandardCharsets.UTF_8));
    }

    @Benchmark
    public WithList jacksonReadStreamWithList() {
        return jackson.readValue(new ByteArrayInputStream(withListBytes), WithList.class);
    }

    // --- write to a byte stream

    @Benchmark
    public int fusionWriteStreamFlat() throws Exception {
        final var out = new ByteArrayOutputStream(1024);
        try (final var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            fusion.write(flat, writer);
        }
        return out.size();
    }

    @Benchmark
    public int fusionWriteByteStreamFlat() { // the byte-stream API (FastUtf8Writer)
        final var out = new ByteArrayOutputStream(1024);
        fusion.write(flat, out);
        return out.size();
    }

    @Benchmark
    public int fusionWriteByteStreamWithList() {
        final var out = new ByteArrayOutputStream(2048);
        fusion.write(withList, out);
        return out.size();
    }

    @Benchmark
    public int jacksonWriteStreamFlat() {
        final var out = new ByteArrayOutputStream(1024);
        jackson.writeValue(out, flat);
        return out.size();
    }

    @Benchmark
    public int fusionWriteStreamWithList() throws Exception {
        final var out = new ByteArrayOutputStream(2048);
        try (final var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            fusion.write(withList, writer);
        }
        return out.size();
    }

    @Benchmark
    public int jacksonWriteStreamWithList() {
        final var out = new ByteArrayOutputStream(2048);
        jackson.writeValue(out, withList);
        return out.size();
    }
}
