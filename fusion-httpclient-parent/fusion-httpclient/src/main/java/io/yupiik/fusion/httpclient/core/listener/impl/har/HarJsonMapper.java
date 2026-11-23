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
package io.yupiik.fusion.httpclient.core.listener.impl.har;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.httpclient.core.listener.impl.FusionModule;
import io.yupiik.fusion.httpclient.core.listener.impl.BaseHARDumperListener.Har;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.internal.JsonStrings;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.yupiik.fusion.json.spi.Parser.Event.VALUE_STRING;

public class HarJsonMapper extends JsonMapperImpl {
    public HarJsonMapper() {
        super(
                List.of(
                        FusionModule.BaseHARDumperListener$Har$Cache$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Header$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$PostData$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$CacheRequest$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Identity$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Query$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Content$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Log$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Request$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Cookie$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Page$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Response$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Entry$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$PageTiming$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Timings$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$FusionJsonCodec(),
                        FusionModule.BaseHARDumperListener$Har$Param$FusionJsonCodec()),
                Configuration.of(Map.of()));
    }

    @Override
    protected Stream<JsonCodec<?>> builtInCodecs() {
        return Stream.concat(
                super.builtInCodecs()
                        .filter(it -> it.type() != ZonedDateTime.class),
                Stream.of(new HarZonedDateTimeJsonCodec()));
    }

    private static class HarZonedDateTimeJsonCodec implements JsonCodec<ZonedDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

        @Override
        public Type type() {
            return ZonedDateTime.class;
        }

        @Override
        public ZonedDateTime read(final DeserializationContext context) {
            final var parser = context.parser();
            if (!parser.hasNext() || parser.next() != VALUE_STRING) {
                throw new IllegalStateException("Expected VALUE_STRING");
            }
            return ZonedDateTime.parse(parser.getString(), FORMATTER);
        }

        @Override
        public void write(final ZonedDateTime value, final SerializationContext context) throws IOException {
            context.writer().write(JsonStrings.escape(value.format(FORMATTER)));
        }
    }
}
