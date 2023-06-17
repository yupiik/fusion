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

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.jwt.internal.JwtImpl;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

// @ApplicationScoped
public class JwtValidatorFactory {
    private final JsonMapper mapper;
    private final Clock clock;

    public JwtValidatorFactory(final JsonMapper mapper, final Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public Function<String, Jwt> newValidator(final JwtValidatorConfiguration configuration) {
        final Base64.Decoder decoder = Base64.getUrlDecoder();
        if (configuration.algo().startsWith("HS")) {
            final var secretKey = new SecretKeySpec(
                    configuration.key().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA" + configuration.algo().substring("hs".length()));
            final var hmacAlgo = switch (configuration.algo()) {
                case "HS256" -> "HmacSHA256";
                case "HS384" -> "HmacSHA384";
                case "HS512" -> "HmacSHA512";
                default -> throw new IllegalArgumentException(configuration.algo());
            };
            return jwt -> {
                final var split = SigningStringAndSignature.of(jwt);
                if (!verifyHmac(hmacAlgo, secretKey, split.signingString(), decoder.decode(split.signature()))) {
                    throw new IllegalArgumentException("Invalid JWT '" + jwt + "'");
                }
                return toJwt(configuration, split.signingString());
            };
        }

        // assume RS or ES - both use signature kind of signing

        final Consumer<Signature> customizer;
        final String signatureAlgo;
        switch (configuration.algo()) {
            case "ES256" -> {
                signatureAlgo = "SHA256withECDSA";
                customizer = null;
            }
            case "ES384" -> {
                signatureAlgo = "SHA384withECDSA";
                customizer = null;
            }
            case "ES512" -> {
                signatureAlgo = "SHA512withECDSA";
                customizer = null;
            }
            case "RS256" -> {
                signatureAlgo = "SHA256withRSA";
                customizer = null;
            }
            case "RS384" -> {
                signatureAlgo = "SHA384withRSA";
                customizer = null;
            }
            case "RS512" -> {
                signatureAlgo = "SHA512withRSA";
                customizer = null;
            }
            case "PS256" -> {
                signatureAlgo = "RSASSA-PSS";

                final var ps256Param = new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, PSSParameterSpec.TRAILER_FIELD_BC);
                customizer = s -> {
                    try {
                        s.setParameter(ps256Param);
                    } catch (final InvalidAlgorithmParameterException e) {
                        throw new IllegalStateException(e);
                    }
                };
            }
            case "PS384" -> {
                signatureAlgo = "RSASSA-PSS";

                final var ps256Param = new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, PSSParameterSpec.TRAILER_FIELD_BC);
                customizer = s -> {
                    try {
                        s.setParameter(ps256Param);
                    } catch (final InvalidAlgorithmParameterException e) {
                        throw new IllegalStateException(e);
                    }
                };
            }
            case "PS512" -> {
                signatureAlgo = "RSASSA-PSS";

                final var ps256Param = new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, PSSParameterSpec.TRAILER_FIELD_BC);
                customizer = s -> {
                    try {
                        s.setParameter(ps256Param);
                    } catch (final InvalidAlgorithmParameterException e) {
                        throw new IllegalStateException(e);
                    }
                };
            }
            default -> throw new IllegalArgumentException(configuration.algo());
        }
        final var publicKey = new PublicKeyLoader().loadPublicKey(configuration.key(), "RSA");
        return jwt -> {
            final var split = SigningStringAndSignature.of(jwt);
            if (!verifySignature(signatureAlgo, publicKey, split.signingString(), decoder.decode(split.signature()), customizer)) {
                throw new IllegalArgumentException("Invalid JWT '" + jwt + "'");
            }
            return toJwt(configuration, split.signingString());
        };
    }

    @SuppressWarnings("unchecked")
    private Jwt toJwt(final JwtValidatorConfiguration configuration, final String signingString) {
        final int sep = signingString.indexOf('.');
        if (sep < 0 || signingString.indexOf('.', sep + 1) >= 0) {
            throw new IllegalArgumentException("Invalid jwt header.payload: '" + signingString + "'");
        }
        final var header = signingString.substring(0, sep);
        final var payload = signingString.substring(sep + 1);
        final var headerData = (Map<String, Object>) mapper.fromBytes(Object.class, Base64.getUrlDecoder().decode(header));
        final var payloadData = (Map<String, Object>) mapper.fromBytes(Object.class, Base64.getUrlDecoder().decode(payload));

        final var typ = headerData.get("typ");
        if (!"JWT".equals(typ)) {
            throw new IllegalArgumentException("Invalid JWT typ: '" + typ + "'");
        }

        final var iss = payloadData.get("iss");
        if (!Objects.equals(iss, configuration.issuer())) {
            throw new IllegalArgumentException("Invalid JWT iss: '" + iss + "'");
        }

        long now = -1;
        final var exp = payloadData.get("exp");
        if (exp instanceof Number number) {
            now = TimeUnit.MILLISECONDS.toSeconds(clock.millis());
            if (number.longValue() + configuration.tolerance() < now) {
                throw new IllegalArgumentException("JWT is expired");
            }
        } else if (configuration.expRequired()) {
            throw new IllegalArgumentException("Missing exp in JWT");
        }

        final var iat = payloadData.get("iat");
        if (iat instanceof Number number) {
            if (now < 0) {
                now = TimeUnit.MILLISECONDS.toSeconds(clock.millis());
            }
            if (number.longValue() > now + configuration.tolerance()) {
                throw new IllegalArgumentException("iat after now, invalid JWT");
            }
        } else if (configuration.iatRequired()) {
            throw new IllegalArgumentException("Missing iat in JWT");
        }

        return new JwtImpl(mapper, header, payload, headerData, payloadData);
    }

    private boolean verifyHmac(final String algo, final SecretKey key, final String signingString, final byte[] expected) {
        try {
            final var mac = Mac.getInstance(algo);
            mac.init(key);
            return MessageDigest.isEqual(mac.doFinal(signingString.getBytes(StandardCharsets.UTF_8)), expected);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean verifySignature(final String algo, final PublicKey key, final String signingString, final byte[] expected,
                                    final Consumer<Signature> customizer) {
        try {
            final var signature = Signature.getInstance(algo);
            if (customizer != null) {
                customizer.accept(signature);
            }
            signature.initVerify(key);
            signature.update(signingString.getBytes(StandardCharsets.UTF_8));
            return signature.verify(expected);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record SigningStringAndSignature(String signingString, String signature) {
        private static SigningStringAndSignature of(String jwt) {
            final int lastDot = jwt.lastIndexOf('.');
            if (lastDot < 0) {
                throw new IllegalArgumentException("No dot in JWT: '" + jwt + "'");
            }
            final var signingString = jwt.substring(0, lastDot);
            final int headerPayloadSep = signingString.indexOf('.');
            if (headerPayloadSep < 0 || headerPayloadSep != signingString.lastIndexOf('.')) {
                throw new IllegalArgumentException("Missing dot in JWT: '" + jwt + "'");
            }
            final var signature = jwt.substring(lastDot + 1);
            return new SigningStringAndSignature(signingString, signature);
        }
    }
}
