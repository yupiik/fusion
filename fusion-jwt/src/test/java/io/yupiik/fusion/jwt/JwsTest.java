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
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwsTest {
    private final Jws jws = new Jws();
    private final byte[] input = "eyJ0eXAiOiJKV1QifQ.cGF5bG9hZA".getBytes(US_ASCII);

    @ParameterizedTest
    @CsvSource({
            "RS256,RSA,SHA256withRSA,256", "RS384,RSA,SHA384withRSA,256", "RS512,RSA,SHA512withRSA,256",
            "PS256,RSA,RSASSA-PSS,256", "PS384,RSA,RSASSA-PSS,256", "PS512,RSA,RSASSA-PSS,256",
            "ES256,secp256r1,SHA256withECDSAinP1363Format,64",
            "ES384,secp384r1,SHA384withECDSAinP1363Format,96",
            "ES512,secp521r1,SHA512withECDSAinP1363Format,132",
            "EdDSA,Ed25519,Ed25519,64", "EdDSA,Ed448,Ed448,114",
            "Ed25519,Ed25519,Ed25519,64", "Ed448,Ed448,Ed448,114"
    })
    void asymmetricInteroperability(final String algorithm, final String keyType, final String jcaAlgorithm,
                                   final int width) throws Exception {
        final var keys = keyPair(keyType);
        final var raw = jws.sign(algorithm, keys.getPrivate(), input);
        assertEquals(width, raw.length);
        assertTrue(jws.verify(algorithm, keys.getPublic(), input, raw));
        assertThrows(IllegalArgumentException.class, () -> jws.sign(algorithm, keys.getPublic(), input));
        assertThrows(IllegalArgumentException.class, () -> jws.verify(algorithm, keys.getPrivate(), input, raw));
        // Independent JCA checks JOSE P1363 encoding and exact PSS parameters in both directions.
        final var verifier = signature(algorithm, jcaAlgorithm);
        verifier.initVerify(keys.getPublic());
        verifier.update(input);
        assertTrue(verifier.verify(raw));
        final var signer = signature(algorithm, jcaAlgorithm);
        signer.initSign(keys.getPrivate());
        signer.update(input);
        assertTrue(jws.verify(algorithm, keys.getPublic(), input, signer.sign()));
        assertFalse(jws.verify(algorithm, keys.getPublic(), "changed".getBytes(US_ASCII), raw));
        assertFalse(jws.verify(algorithm, keyPair(keyType).getPublic(), input, raw));
        final var tampered = raw.clone();
        tampered[tampered.length - 1] ^= 1;
        assertFalse(jws.verify(algorithm, keys.getPublic(), input, tampered));
        assertFalse(jws.verify(algorithm, keys.getPublic(), input, Arrays.copyOf(raw, raw.length - 1)));
    }

    @ParameterizedTest
    @CsvSource({"HS256,HmacSHA256,32", "HS384,HmacSHA384,48", "HS512,HmacSHA512,64"})
    void hmacInteroperabilityAndStrength(final String algorithm, final String jcaAlgorithm, final int bytes) throws Exception {
        final var material = new byte[bytes];
        Arrays.fill(material, (byte) 0xa5);
        final var key = new SecretKeySpec(material, jcaAlgorithm);
        final var mac = Mac.getInstance(jcaAlgorithm);
        mac.init(key);
        final var expected = mac.doFinal(input);
        assertArrayEquals(expected, jws.sign(algorithm, key, input));
        assertTrue(jws.verify(algorithm, key, input, expected));
        assertFalse(jws.verify(algorithm, key, "changed".getBytes(US_ASCII), expected));
        final var tampered = expected.clone();
        tampered[0] ^= 1;
        assertFalse(jws.verify(algorithm, key, input, tampered));
        assertFalse(jws.verify(algorithm, key, input, Arrays.copyOf(expected, expected.length - 1)));
        material[0] ^= 1;
        assertFalse(jws.verify(algorithm, new SecretKeySpec(material, jcaAlgorithm), input, expected));
        final var weak = new SecretKeySpec(new byte[bytes - 1], jcaAlgorithm);
        assertThrows(IllegalArgumentException.class, () -> jws.sign(algorithm, weak, input));
        assertThrows(IllegalArgumentException.class, () -> jws.verify(algorithm, weak, input, expected));
        final var longer = new SecretKeySpec(new byte[bytes + 1], jcaAlgorithm);
        assertTrue(jws.verify(algorithm, longer, input, jws.sign(algorithm, longer, input)));
        final var rsa = keyPair("RSA");
        assertThrows(IllegalArgumentException.class, () -> jws.sign(algorithm, rsa.getPrivate(), input));
        assertThrows(IllegalArgumentException.class, () -> jws.verify(algorithm, rsa.getPublic(), input, expected));
    }

    @Test
    void rfc4231HmacSha256TestCase6() {
        // RFC 4231 section 4.6: external expected value, not computed by Fusion or JCA.
        final var material = new byte[131];
        Arrays.fill(material, (byte) 0xaa);
        final var key = new SecretKeySpec(material, "HmacSHA256");
        final var data = "Test Using Larger Than Block-Size Key - Hash Key First".getBytes(US_ASCII);
        final var expected = HexFormat.of().parseHex("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54");
        assertArrayEquals(expected, jws.sign("HS256", key, data));
        assertTrue(jws.verify("HS256", key, data, expected));
    }

    @ParameterizedTest
    @CsvSource({"ES256,secp384r1", "ES256,secp521r1", "ES384,secp256r1", "ES384,secp521r1",
            "ES512,secp256r1", "ES512,secp384r1", "Ed25519,Ed448", "Ed448,Ed25519"})
    void rejectsWrongCurve(final String algorithm, final String curve) throws Exception {
        final var keys = keyPair(curve);
        assertThrows(IllegalArgumentException.class, () -> jws.sign(algorithm, keys.getPrivate(), input));
        assertThrows(IllegalArgumentException.class, () -> jws.verify(algorithm, keys.getPublic(), input, new byte[132]));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HS256", "HS384", "HS512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512",
            "ES256", "ES384", "ES512", "EdDSA", "Ed25519", "Ed448", "none", "unknown"})
    void rejectsWrongKeyFamilyAndUnsupportedAlgorithms(final String algorithm) throws Exception {
        final var keys = keyPair(algorithm.startsWith("RS") || algorithm.startsWith("PS") || algorithm.startsWith("HS") ?
                "secp256r1" : "RSA");
        assertThrows(IllegalArgumentException.class, () -> jws.sign(algorithm, keys.getPrivate(), input));
        assertThrows(IllegalArgumentException.class, () -> jws.verify(algorithm, keys.getPublic(), input, new byte[64]));
        if (!algorithm.startsWith("HS")) {
            final var secret = new SecretKeySpec(new byte[64], "HmacSHA512");
            assertThrows(IllegalArgumentException.class, () -> jws.sign(algorithm, secret, input));
            assertThrows(IllegalArgumentException.class, () -> jws.verify(algorithm, secret, input, new byte[64]));
        }
    }

    @ParameterizedTest
    @CsvSource({"ES256,secp256r1,SHA256withECDSA,64", "ES384,secp384r1,SHA384withECDSA,96",
            "ES512,secp521r1,SHA512withECDSA,132"})
    void rejectsDerAndMalformedEcSignatures(final String algorithm, final String curve, final String jcaAlgorithm,
                                            final int width) throws Exception {
        final var keys = keyPair(curve);
        final var signer = Signature.getInstance(jcaAlgorithm);
        signer.initSign(keys.getPrivate());
        signer.update(input);
        assertFalse(jws.verify(algorithm, keys.getPublic(), input, signer.sign()));
        assertFalse(jws.verify(algorithm, keys.getPublic(), input, new byte[width]));
        assertFalse(jws.verify(algorithm, keys.getPublic(), input, new byte[width + 1]));
    }

    @Test
    void preservesEcPemLoading() throws Exception {
        final var keys = keyPair("secp256r1");
        final var delimiter = "-".repeat(5);
        final var pem = delimiter + "BEGIN PRIVATE KEY" + delimiter + '\n' +
                Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()) + '\n' +
                delimiter + "END PRIVATE KEY" + delimiter;
        assertEquals(keys.getPrivate(), new PrivateKeyLoader().loadPrivateKey(pem, "EC"));
        assertThrows(IllegalStateException.class, () -> new PrivateKeyLoader().loadPrivateKey(pem, "RSA"));
    }

    private Signature signature(final String algorithm, final String jcaAlgorithm) throws Exception {
        final var signature = Signature.getInstance(jcaAlgorithm);
        if (algorithm.startsWith("PS")) {
            final var bits = Integer.parseInt(algorithm.substring(2));
            signature.setParameter(new PSSParameterSpec("SHA-" + bits, "MGF1", new MGF1ParameterSpec("SHA-" + bits),
                    bits / 8, PSSParameterSpec.TRAILER_FIELD_BC));
        }
        return signature;
    }

    private KeyPair keyPair(final String type) throws Exception {
        final var generator = KeyPairGenerator.getInstance(type.startsWith("secp") ? "EC" : type);
        if (type.startsWith("secp")) {
            generator.initialize(new ECGenParameterSpec(type));
        } else if ("RSA".equals(type)) {
            generator.initialize(2048);
        }
        return generator.generateKeyPair();
    }
}
