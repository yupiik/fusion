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
package io.yupiik.fusion.httpclient.core.listener.impl;

import io.yupiik.fusion.httpclient.core.request.UnlockedHttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class HARDumperListenerTest {

    @Test
    void harJson(@TempDir final Path temp) throws Exception {
        final var output = temp.resolve("harJson.json");
        final String expected = "{\"log\":{\"comment\":\"\",\"entries\":[" +
                "{\"comment\":\"\",\"request\":{\"bodySize\":-1,\"comment\":\"\",\"headerSize\":0,\"headers\":[]," +
                "\"httpVersion\":\"HTTP/1.1\",\"method\":\"GET\",\"url\":\"http://localhost:1234/test1\"}," +
                "\"response\":{\"bodySize\":0,\"comment\":\"\",\"headers\":[],\"headersSize\":0,\"httpVersion\":\"HTTP/1.1\"," +
                "\"status\":200,\"statusText\":\"\"},\"startedDateTime\":\"1970-01-01T00:00:00.000Z\",\"time\":1}],\"version\":\"1.2\"}}";

        var clock = new Clock() {
            private Instant instant = Instant.EPOCH;
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return instant;
            }

            public void plusMillis(long value) {
                instant = instant.plusMillis(value);
            }
        };
        final HARDumperListener.Configuration configuration = new HARDumperListener.Configuration(
            output,
            clock,
            Logger.getLogger("test"));

        try (final var listener = new HARDumperListener(configuration)) {

            final var request = requestGet();
            final var before = listener.before(1, request);
            clock.plusMillis(1);
            listener.after(before.state(), before.request(), null, response(200, request));
        }

        assertEquals(expected, Files.readString(output));
    }
    @Test
    void harJsonTimeDisable(@TempDir final Path temp) throws Exception {
        final var output = temp.resolve("harJsonTimeDisable.json");
        final String expected = "{\"log\":{\"comment\":\"\",\"entries\":[" +
                "{\"comment\":\"\",\"request\":{\"bodySize\":-1,\"comment\":\"\",\"headerSize\":0,\"headers\":[]," +
                "\"httpVersion\":\"HTTP/1.1\",\"method\":\"GET\",\"url\":\"http://localhost:1234/test1\"}," +
                "\"response\":{\"bodySize\":0,\"comment\":\"\",\"headers\":[],\"headersSize\":0,\"httpVersion\":\"HTTP/1.1\"," +
                "\"status\":200,\"statusText\":\"\"}}],\"version\":\"1.2\"}}";

        var clock = new Clock() {
            private Instant instant = Instant.EPOCH;
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return instant;
            }

            public void plusMillis(long value) {
                instant = instant.plusMillis(value);
            }
        };
        final HARDumperListener.Configuration configuration = new HARDumperListener.Configuration(
            output,
            clock,
            Logger.getLogger("test")).setEnableTime(false).setEnableStartedDateTime(false);

        try (final var listener = new HARDumperListener(configuration)) {

            final var request = requestGet();
            final var before = listener.before(1, request);
            clock.plusMillis(1);
            listener.after(before.state(), before.request(), null, response(200, request));
        }

        assertEquals(expected, Files.readString(output));
    }

    private HttpRequest requestGet() {
        return new UnlockedHttpRequest("GET",
            URI.create("http://localhost:1234/test1"),
            HttpHeaders.of(Map.of(), (a, b) -> true));
    }

    private HttpResponse<String> response(final int status, final HttpRequest request) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return null;
            }

            @Override
            public String body() {
                return "";
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    @Test
    void configurationTest() {
        var myTestConfig = new TestConfiguration(Path.of("/"), Clock.systemUTC(), Logger.getLogger("test"))
            .setEnableTime(false);
        assertInstanceOf(TestConfiguration.class, myTestConfig);
        assertEquals(false, myTestConfig.isEnableTime());
        assertEquals(true, myTestConfig.isEnableStartedDateTime());
        myTestConfig = myTestConfig.setFoo("foo").setEnableStartedDateTime(false);
        assertInstanceOf(TestConfiguration.class, myTestConfig);
        assertEquals("foo", myTestConfig.getFoo());
        assertEquals(false, myTestConfig.isEnableTime());
        assertEquals(false, myTestConfig.isEnableStartedDateTime());
        myTestConfig = myTestConfig.setEnableTime(true).setFoo("bar");
        assertInstanceOf(TestConfiguration.class, myTestConfig);
        assertEquals("bar", myTestConfig.getFoo());
        assertEquals(true, myTestConfig.isEnableTime());


        var myBaseConfig = new BaseHARDumperListener.BaseConfiguration(Path.of("/"), Clock.systemUTC(), Logger.getLogger("test"));
        assertInstanceOf(BaseHARDumperListener.BaseConfiguration.class, myBaseConfig);
        assertEquals(true, myBaseConfig.isEnableTime());
        assertEquals(true, myBaseConfig.isEnableStartedDateTime());
        myBaseConfig = myBaseConfig.setEnableTime(false).setEnableStartedDateTime(false);
        assertEquals(false, myBaseConfig.isEnableTime());
        assertEquals(false, myBaseConfig.isEnableStartedDateTime());
        assertInstanceOf(BaseHARDumperListener.BaseConfiguration.class, myBaseConfig);

    }

    private static final class TestConfiguration extends BaseHARDumperListener.BaseConfiguration<TestConfiguration> {

        private String foo;
        public TestConfiguration(Path output, Clock clock, Logger logger) {
            super(output, clock, logger);
        }

        public String getFoo() {
            return foo;
        }

        public TestConfiguration setFoo(String foo) {
            this.foo = foo;
            return this;
        }
    }

}
