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
package io.yupiik.fusion.jwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtCryptoTest {
    private final Clock clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final JwtSignerFactory signers = new JwtSignerFactory(empty(), of(clock));
    private final JwtValidatorFactory validators = new JwtValidatorFactory(empty(), of(clock));
    private final Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
    // Not base64: the configuration must use these exact UTF-8 bytes, without trimming or decoding.
    private final String secret = " test-\u00e9-raw-secret ".repeat(4);

    @ParameterizedTest
    @CsvSource({"HS256,HmacSHA256", "HS384,HmacSHA384", "HS512,HmacSHA512",
            "RS256,RSA", "RS384,RSA", "RS512,RSA", "PS256,RSA", "PS384,RSA", "PS512,RSA",
            "ES256,secp256r1", "ES384,secp384r1", "ES512,secp521r1",
            "EdDSA,Ed25519", "EdDSA,Ed448", "Ed25519,Ed25519", "Ed448,Ed448"})
    void configuredKeyRoundTripsAndPinnedAlgorithm(final String algorithm, final String type) throws Exception {
        final Key signingKey;
        final Key verificationKey;
        if (algorithm.startsWith("HS")) {
            signingKey = new SecretKeySpec(secret.getBytes(UTF_8), type);
            verificationKey = signingKey;
        } else {
            final var generator = KeyPairGenerator.getInstance(type.startsWith("secp") ? "EC" : type);
            if (type.startsWith("secp")) {
                generator.initialize(new ECGenParameterSpec(type));
            } else if ("RSA".equals(type)) {
                generator.initialize(2048);
            }
            final var pair = generator.generateKeyPair();
            signingKey = pair.getPrivate();
            verificationKey = pair.getPublic();
        }

        // Explicitly exercise the new Key overload, as well as the retained PrivateKey overload below.
        final var rawSigner = signers.newSigner(algorithm, signingKey);
        final var input = "e30.e30";
        assertTrue(new Jws().verify(algorithm, verificationKey, input.getBytes(UTF_8), rawSigner.apply(input)));
        if (signingKey instanceof PrivateKey privateKey) {
            assertTrue(new Jws().verify(algorithm, verificationKey, input.getBytes(UTF_8),
                    signers.newSigner(algorithm, privateKey).apply(input)));
        }

        // Both PKCS8 private and X509 public keys must load as bare base64 and as PEM.
        for (final var pem : List.of(false, true)) {
            final var configuredPrivate = algorithm.startsWith("HS") ? secret : encoded(signingKey, "PRIVATE", pem);
            final var configuredPublic = algorithm.startsWith("HS") ? secret : encoded(verificationKey, "PUBLIC", pem);
            final var signer = signers.newJwtFactory(new JwtSignerConfiguration(
                    configuredPrivate, algorithm, "https://issuer.test", "known", true, 60_000, true, true));
            final var validator = validators.newValidator(new JwtValidatorConfiguration(
                    configuredPublic, algorithm, "https://issuer.test", 0, true, true, true, true, List.of()));
            final var token = signer.apply(Map.of("sub", "alice"));
            final var jwt = validator.apply(token);
            assertEquals("alice", jwt.claim("sub", String.class).orElseThrow());
            assertEquals("https://issuer.test", jwt.claim("iss", String.class).orElseThrow());
            assertEquals(clock.instant().getEpochSecond() + 60, jwt.claim("exp", Number.class).orElseThrow().longValue());
            final var parts = token.split("\\.");
            final var signingInput = parts[0] + '.' + parts[1];
            assertTrue(new Jws().verify(algorithm, verificationKey, signingInput.getBytes(UTF_8),
                    Base64.getUrlDecoder().decode(parts[2])));
            final var changedPayload = url.encodeToString(new String(Base64.getUrlDecoder().decode(parts[1]), UTF_8)
                    .replace("\"alice\"", "\"mallory\"").getBytes(UTF_8));
            assertThrows(IllegalArgumentException.class,
                    () -> validator.apply(parts[0] + '.' + changedPayload + '.' + parts[2]));

            // These tokens have valid signatures under the PINNED algorithm, not the advertised one.
            // Merely ignoring the header alg would wrongly accept them.
            for (final var advertised : List.of("none", "unknown", algorithm.equals("HS256") ? "HS384" : "HS256")) {
                final var header = "{\"typ\":\"JWT\",\"kid\":\"known\",\"alg\":\"" + advertised + "\"}";
                assertThrows(IllegalArgumentException.class,
                        () -> validator.apply(token(header, algorithm, signingKey)));
            }
            assertThrows(IllegalArgumentException.class,
                    () -> validator.apply(token("{\"typ\":\"JWT\",\"kid\":\"known\"}", algorithm, signingKey)));
        }
    }

    @ParameterizedTest
    @CsvSource({"EdDSA,Ed25519", "EdDSA,Ed448", "Ed25519,Ed25519", "Ed448,Ed448"})
    void okpJwkAndKidSelection(final String algorithm, final String curve) throws Exception {
        final var pair = KeyPairGenerator.getInstance(curve).generateKeyPair();
        final var publicKey = (EdECPublicKey) pair.getPublic();
        // RFC 8037 encodes the Edwards y coordinate little-endian, with the x parity in the top bit.
        final var raw = new byte[curve.equals("Ed25519") ? 32 : 57];
        final var bigEndian = publicKey.getPoint().getY().toByteArray();
        for (int i = 0; i < raw.length && i < bigEndian.length; i++) {
            raw[i] = bigEndian[bigEndian.length - 1 - i];
        }
        if (publicKey.getPoint().isXOdd()) {
            raw[raw.length - 1] |= (byte) 0x80;
        }
        final var jwk = new JwtValidatorConfiguration.JwkKey(
                "known", "OKP", algorithm, "sig", null, null, null, curve, url.encodeToString(raw), null);
        // A usable default key must not become a fallback for missing/unknown kid in a configured set.
        final var validator = validators.newValidator(new JwtValidatorConfiguration(
                encoded(pair.getPublic(), "PUBLIC", false), algorithm, "https://issuer.test",
                0, true, true, true, true, List.of(jwk)));
        final var validHeader = "{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\",\"kid\":\"known\"}";
        assertEquals("alice", validator.apply(token(validHeader, algorithm, pair.getPrivate()))
                .claim("sub", String.class).orElseThrow());
        for (final var kid : List.of(
                "", ",\"kid\":\"unknown\"", ",\"kid\":123")) {
            final var header = "{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"" + kid + '}';
            // The contract requires rejection, without prescribing the exception subtype for key lookup.
            assertThrows(RuntimeException.class, () -> validator.apply(token(header, algorithm, pair.getPrivate())));
        }
        final var mismatched = "{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\"known\"}";
        assertThrows(IllegalArgumentException.class, () -> validator.apply(token(mismatched, algorithm, pair.getPrivate())));
        final var other = KeyPairGenerator.getInstance(curve).generateKeyPair();
        assertThrows(IllegalArgumentException.class, () -> validator.apply(token(validHeader, algorithm, other.getPrivate())));
    }

    @ParameterizedTest
    @CsvSource({"HS256,31", "HS384,47", "HS512,63"})
    void rejectsWeakConfiguredSecrets(final String algorithm, final int bytes) throws Exception {
        final var weak = "x".repeat(bytes);
        assertThrows(IllegalArgumentException.class, () -> signers.newJwtFactory(new JwtSignerConfiguration(
                weak, algorithm, "https://issuer.test", "known", true, 60_000, true, true))
                .apply(Map.of("sub", "alice")));
        // JCA permits short keys: produce an otherwise valid token to isolate the strength check.
        final var header = "{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}";
        final var payload = "{\"iss\":\"https://issuer.test\",\"exp\":" + (clock.instant().getEpochSecond() + 60) + '}';
        final var input = url.encodeToString(header.getBytes(UTF_8)) + '.' + url.encodeToString(payload.getBytes(UTF_8));
        final var mac = Mac.getInstance("HmacSHA" + algorithm.substring(2));
        mac.init(new SecretKeySpec(weak.getBytes(UTF_8), mac.getAlgorithm()));
        final var token = input + '.' + url.encodeToString(mac.doFinal(input.getBytes(UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> validators.newValidator(new JwtValidatorConfiguration(
                weak, algorithm, "https://issuer.test")).apply(token));
    }

    @Test
    void rejectsKeySetsWithoutUsableSignatureKeys() {
        final var key = new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256");
        final var token = token("{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"ignored\"}", "HS256", key);
        for (final var jwk : List.of(
                new JwtValidatorConfiguration.JwkKey("ignored", "RSA", "RS256", "enc", null, null, null, null, null, null),
                new JwtValidatorConfiguration.JwkKey("ignored", "oct", "HS256", "sig", null, null, null, null, null, null))) {
            final var validator = validators.newValidator(new JwtValidatorConfiguration(
                    secret, "HS256", "https://issuer.test", 0, true, true, true, true, List.of(jwk)));
            assertThrows(IllegalArgumentException.class, () -> validator.apply(token));
        }
    }

    @ParameterizedTest
    @CsvSource({"rs256,RS256,RSA", "eddsa,EdDSA,Ed25519", "ed25519,Ed25519,Ed25519", "ed448,Ed448,Ed448"})
    void normalizesSignerAlgorithm(final String configured, final String canonical, final String keyType) throws Exception {
        final var pair = KeyPairGenerator.getInstance(keyType).generateKeyPair();
        final var signer = signers.newJwtFactory(new JwtSignerConfiguration(
                encoded(pair.getPrivate(), "PRIVATE", false), configured, "https://issuer.test", "known", true, 60_000, true, true));
        final var validator = validators.newValidator(new JwtValidatorConfiguration(
                encoded(pair.getPublic(), "PUBLIC", false), canonical, "https://issuer.test"));
        assertEquals("alice", validator.apply(signer.apply(Map.of("sub", "alice"))).claim("sub", String.class).orElseThrow());
    }

    private String token(final String header, final String algorithm, final Key key) {
        final var now = clock.instant().getEpochSecond();
        final var payload = "{\"iss\":\"https://issuer.test\",\"sub\":\"alice\",\"jti\":\"test\",\"iat\":" + now +
                ",\"nbf\":" + now + ",\"exp\":" + (now + 60) + '}';
        final var input = url.encodeToString(header.getBytes(UTF_8)) + '.' + url.encodeToString(payload.getBytes(UTF_8));
        return input + '.' + url.encodeToString(signers.newSigner(algorithm, key).apply(input));
    }

    private String encoded(final Key key, final String type, final boolean pem) {
        final var base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        if (!pem) {
            return base64;
        }
        final var delimiter = "-".repeat(5);
        return delimiter + "BEGIN " + type + " KEY" + delimiter + '\n' + base64 + '\n' +
                delimiter + "END " + type + " KEY" + delimiter;
    }
}
