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
package io.yupiik.fusion.jwt;

import io.yupiik.fusion.json.internal.JsonMapperImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import static java.time.Clock.fixed;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class JwtValidatorFactoryTest {
    private final Clock clock = fixed(Instant.EPOCH.plusMillis(120_000), ZoneId.of("UTC"));
    private final KeyPair key;
    private final Function<String, Jwt> validator;

    JwtValidatorFactoryTest() throws NoSuchAlgorithmException {
        final var keyGenerator = KeyPairGenerator.getInstance("RSA");
        keyGenerator.initialize(1024);
        key = keyGenerator.generateKeyPair();
        validator = new JwtValidatorFactory(
                of(new JsonMapperImpl(List.of(), k -> empty())),
                of(clock))
                .newValidator(new JwtValidatorConfiguration("" +
                        "-----BEGIN PUBLIC KEY-----\n" +
                        Base64.getEncoder().encodeToString(key.getPublic().getEncoded()) + "\n" +
                        "-----END PUBLIC KEY-----\n",
                        "RS256",
                        "http://yupiik.test/oauth2/"));
    }

    @Test
    void validate() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        final var now = clock.instant().getEpochSecond();
        final var header = validHeader();
        final var payload = validPayload(now);
        final var validated = validate(header, payload);
        assertEquals("yupiik", validated.claim("sub", String.class).orElseThrow());
    }

    @Test
    void invalidTyp() {
        final var now = clock.instant().getEpochSecond();
        final var header = "{\"alg\":\"RS256\",\"typ\":\"JOSE\"}";
        final var payload = validPayload(now);
        assertThrows(IllegalArgumentException.class, () -> validate(header, payload));
    }

    @Test
    void invalidIss() {
        final var now = clock.instant().getEpochSecond();
        final var header = validHeader();
        final var payload = "{\"test\":true,\"iss\":\"http://yupiik.test/oauth2\",\"sub\":\"yupiik\",\"iat\":" + (now - 1) + ",\"exp\":" + (now + 10) + "}";
        assertThrows(IllegalArgumentException.class, () -> validate(header, payload));
    }

    @Test
    void invalidExp() {
        final var now = clock.instant().getEpochSecond();
        final var header = validHeader();
        final var payload = "{\"test\":true,\"iss\":\"http://yupiik.test/oauth2/\",\"sub\":\"yupiik\",\"iat\":" + (now - 32) + ",\"exp\":" + (now - 31) + "}";
        assertThrows(IllegalArgumentException.class, () -> validate(header, payload));
    }

    @Test
    void invalidIat() {
        final var now = clock.instant().getEpochSecond();
        final var header = validHeader();
        final var payload = "{\"test\":true,\"iss\":\"http://yupiik.test/oauth2/\",\"sub\":\"yupiik\",\"iat\":" + (now + 32) + ",\"exp\":" + (now + 35) + "}";
        assertThrows(IllegalArgumentException.class, () -> validate(header, payload));
    }

    @Test
    void invalidNbf() {
        final var now = clock.instant().getEpochSecond();
        final var header = validHeader();
        final var payload = "{\"test\":true,\"iss\":\"http://yupiik.test/oauth2/\",\"sub\":\"yupiik\",\"nbf\":" + (now + 32) + ",\"exp\":" + (now + 35) + "}";
        assertThrows(IllegalArgumentException.class, () -> validate(header, payload));
    }

    private String validHeader() {
        return "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
    }

    private String validPayload(final long now) {
        return "{\"test\":true,\"iss\":\"http://yupiik.test/oauth2/\",\"sub\":\"yupiik\",\"iat\":" + (now - 1) + ",\"exp\":" + (now + 1) + "}";
    }

    private Jwt validate(final String header, final String payload) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        final var encoder = Base64.getUrlEncoder().withoutPadding();
        final var jwtSigning = encoder.encodeToString(header.getBytes()) + '.' + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        final var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key.getPrivate());
        signature.update(jwtSigning.getBytes(StandardCharsets.UTF_8));
        final var sign = signature.sign();
        final var jwt = jwtSigning + '.' + encoder.encodeToString(sign);
        final var validated = validator.apply(jwt);
        return validated;
    }
}
