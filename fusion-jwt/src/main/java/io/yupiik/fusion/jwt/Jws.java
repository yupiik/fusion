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

import io.yupiik.fusion.framework.api.scope.ApplicationScoped;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECKey;
import java.security.interfaces.EdECKey;
import java.security.interfaces.RSAKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

/**
 * Raw JWS cryptography, without JWT claim enrichment or payload serialization.
 * Callers supply the exact signing input and select an allowed algorithm independently
 * of untrusted headers. ECDSA signatures use JOSE fixed-width R || S, not ASN.1 DER.
 * Algorithm availability depends on the installed JCA providers. Unsecured JWS is not supported.
 */
@ApplicationScoped
public class Jws {
    public static final String ES256 = "ES256";
    public static final String RS256 = "RS256";

    public String base64Url(final byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public byte[] fromBase64Url(final String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public byte[] sign(final String algorithm, final Key key, final byte[] signingInput) {
        final var jcaAlgorithm = algorithm(algorithm, key);
        if (key instanceof SecretKey) {
            return hmac(jcaAlgorithm, key, signingInput);
        }
        if (!(key instanceof PrivateKey privateKey)) {
            throw new IllegalArgumentException("Signing requires a private key: " + algorithm);
        }
        try {
            final var signature = signature(algorithm, jcaAlgorithm);
            signature.initSign(privateKey);
            signature.update(signingInput);
            return signature.sign();
        } catch (final GeneralSecurityException e) {
            throw new IllegalArgumentException("Unable to sign JWS with " + algorithm, e);
        }
    }

    /** @return false for an invalid signature; unsupported algorithms and incompatible keys are rejected. */
    public boolean verify(final String algorithm, final Key key, final byte[] signingInput, final byte[] value) {
        final var jcaAlgorithm = algorithm(algorithm, key);
        if (key instanceof SecretKey) {
            return MessageDigest.isEqual(hmac(jcaAlgorithm, key, signingInput), value);
        }
        if (!(key instanceof PublicKey publicKey)) {
            throw new IllegalArgumentException("Verification requires a public key: " + algorithm);
        }
        final var expectedLength = switch (algorithm) {
            case "ES256" -> 64;
            case "ES384" -> 96;
            case "ES512" -> 132;
            case "Ed25519" -> 64;
            case "Ed448" -> 114;
            case "EdDSA" -> "Ed25519".equals(((EdECKey) key).getParams().getName()) ? 64 : 114;
            default -> -1;
        };
        if (expectedLength > 0 && value.length != expectedLength) {
            return false;
        }
        try {
            final var signature = signature(algorithm, jcaAlgorithm);
            signature.initVerify(publicKey);
            signature.update(signingInput);
            try {
                return signature.verify(value);
            } catch (final SignatureException e) {
                return false;
            }
        } catch (final GeneralSecurityException e) {
            throw new IllegalArgumentException("Unable to verify JWS with " + algorithm, e);
        }
    }

    private byte[] hmac(final String algorithm, final Key key, final byte[] input) {
        try {
            final var mac = Mac.getInstance(algorithm);
            mac.init(key);
            return mac.doFinal(input);
        } catch (final GeneralSecurityException e) {
            throw new IllegalArgumentException("Unable to compute JWS MAC with " + algorithm, e);
        }
    }

    private Signature signature(final String algorithm, final String jcaAlgorithm) throws GeneralSecurityException {
        final var signature = Signature.getInstance(jcaAlgorithm);
        if (algorithm.startsWith("PS")) {
            final var bits = Integer.parseInt(algorithm.substring(2));
            signature.setParameter(new PSSParameterSpec("SHA-" + bits, "MGF1",
                    new MGF1ParameterSpec("SHA-" + bits), bits / 8, PSSParameterSpec.TRAILER_FIELD_BC));
        }
        return signature;
    }

    private String algorithm(final String algorithm, final Key key) {
        return switch (algorithm) {
            case "HS256", "HS384", "HS512" -> {
                final var bits = Integer.parseInt(algorithm.substring(2));
                if (!(key instanceof SecretKey)) {
                    throw new IllegalArgumentException(algorithm + " requires a secret key");
                }
                final var encoded = key.getEncoded();
                if (encoded != null && encoded.length < bits / 8) {
                    throw new IllegalArgumentException(algorithm + " requires at least " + bits + " key bits");
                }
                yield "HmacSHA" + bits;
            }
            case "RS256", "RS384", "RS512", "PS256", "PS384", "PS512" -> {
                if (!(key instanceof RSAKey)) {
                    throw new IllegalArgumentException(algorithm + " requires an RSA key");
                }
                yield algorithm.startsWith("PS") ? "RSASSA-PSS" : "SHA" + algorithm.substring(2) + "withRSA";
            }
            case "ES256", "ES384", "ES512" -> {
                final var curve = switch (algorithm) {
                    case "ES256" -> "secp256r1";
                    case "ES384" -> "secp384r1";
                    default -> "secp521r1";
                };
                if (!(key instanceof ECKey ec) || !matchesCurve(ec, curve)) {
                    throw new IllegalArgumentException(algorithm + " requires a " + curve + " key");
                }
                yield "SHA" + algorithm.substring(2) + "withECDSAinP1363Format";
            }
            case "EdDSA", "Ed25519", "Ed448" -> {
                if (!(key instanceof EdECKey ed) ||
                        !("Ed25519".equals(ed.getParams().getName()) || "Ed448".equals(ed.getParams().getName())) ||
                        (!"EdDSA".equals(algorithm) && !algorithm.equals(ed.getParams().getName()))) {
                    throw new IllegalArgumentException(algorithm + " requires a matching Ed25519 or Ed448 key");
                }
                yield ed.getParams().getName();
            }
            default -> throw new IllegalArgumentException("Unsupported JWS algorithm: " + algorithm);
        };
    }

    private boolean matchesCurve(final ECKey key, final String curve) {
        if (key.getParams() == null) {
            return false;
        }
        try {
            final var parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec(curve));
            final var expected = parameters.getParameterSpec(ECParameterSpec.class);
            final var actual = key.getParams();
            return expected.getCurve().equals(actual.getCurve()) &&
                    expected.getGenerator().equals(actual.getGenerator()) &&
                    expected.getOrder().equals(actual.getOrder()) && expected.getCofactor() == actual.getCofactor();
        } catch (final GeneralSecurityException e) {
            throw new IllegalArgumentException("Unsupported EC curve: " + curve, e);
        }
    }
}
