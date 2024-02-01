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
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.jwt.internal.JwtImpl;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class JwtValidatorFactory {
    private final JsonMapper mapper;
    private final Clock clock;

    public JwtValidatorFactory(final Optional<JsonMapper> mapper, final Optional<Clock> clock) { // also used by proxies
        this.mapper = mapper == null ? null : mapper.orElseGet(() -> new JsonMapperImpl(List.of(), c -> empty()));
        this.clock = clock == null ? null : clock.orElseGet(Clock::systemUTC);
    }

    public Function<String, Jwt> newValidator(final JwtValidatorConfiguration configuration) {
        final var decoder = Base64.getUrlDecoder();
        if (configuration.algo() != null && configuration.algo().startsWith("HS")) {
            final var secretKey = new SecretKeySpec(
                    configuration.key().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA" + configuration.algo().substring("hs".length()));
            final var hmacAlgo = switch (configuration.algo()) {
                case "HS256" -> "HmacSHA256";
                case "HS384" -> "HmacSHA384";
                case "HS512" -> "HmacSHA512";
                default -> throw new IllegalArgumentException(configuration.algo());
            };
            return jwt -> { // this is not in public keys (insecured) so we can always verify with default key
                final var split = SigningStringAndSignature.of(jwt);
                if (!verifyHmac(hmacAlgo, secretKey, split.signingString(), decoder.decode(split.signature()))) {
                    throw new IllegalArgumentException("Invalid JWT '" + jwt + "'");
                }
                return toJwt(configuration, Map.of(), split);
            };
        }

        final var hasKeys = !configuration.keys().isEmpty();
        final var keyValidators = configuration.keys().stream()
                .filter(it -> ("sig".equals(it.use()) || it.use() == null) && ("RSA".equals(it.kty()) || "EC".equals(it.kty())))
                .collect(toMap(JwtValidatorConfiguration.JwkKey::kid, k -> prepareValidator(k, decoder)));
        final var defaultValidator = configuration.algo() == null || configuration.algo().isBlank() ?
                null :
                prepareValidator(new JwtValidatorConfiguration.JwkKey(
                        "",
                        configuration.algo().startsWith("RS") ? "RSA" : "EC",
                        configuration.algo(),
                        "sig",
                        null, null, List.of(configuration.key()), null, null, null), decoder);
        return jwt -> {
            final var split = SigningStringAndSignature.of(jwt);
            if (!hasKeys && !defaultValidator.test(split)) {
                throw new IllegalArgumentException("Invalid JWT '" + jwt + "'");
            }
            return toJwt(configuration, keyValidators, split);
        };
    }

    protected Predicate<SigningStringAndSignature> prepareValidator(final JwtValidatorConfiguration.JwkKey jwkKey, final Base64.Decoder decoder) {
        final Consumer<Signature> customizer;
        final String signatureAlgo;
        switch (jwkKey.alg()) {
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
            default -> throw new IllegalArgumentException(jwkKey.alg());
        }
        final var publicKey = loadKey(jwkKey);
        return split -> verifySignature(signatureAlgo, publicKey, split.signingString(), decoder.decode(split.signature()), customizer);
    }

    protected PublicKey loadKey(final JwtValidatorConfiguration.JwkKey jwkKey) {
        if (jwkKey.x5c() != null && !jwkKey.x5c().isEmpty()) { // shortcut, "hack" for default case
            final var maybePEM = jwkKey.x5c().get(0);
            if (maybePEM.contains("-----BEGIN")) {
                return new PublicKeyLoader().loadPublicKey(maybePEM, jwkKey.kty());
            }
            try {
                final var keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(maybePEM));
                switch (jwkKey.kty()) {
                    case "RSA" -> {
                        try {
                            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
                        } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    case "EC" -> {
                        try {
                            return KeyFactory.getInstance("EC").generatePublic(keySpec);
                        } catch (final InvalidKeySpecException | NoSuchAlgorithmException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    default -> throw new IllegalArgumentException("Unsupported kty='" + jwkKey.kty() + "'");
                }
            } catch (final IllegalStateException ise) {
                try {
                    return new PublicKeyLoader().loadPublicKey(maybePEM, jwkKey.kty());
                } catch (final IllegalArgumentException iae) {
                    // no-op, try standard key
                }
            }
        }

        return switch (jwkKey.kty()) {
            case "RSA" -> {
                final var modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwkKey.n()));
                final var exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwkKey.e()));
                final var spec = new RSAPublicKeySpec(modulus, exponent);
                try {
                    yield KeyFactory.getInstance("RSA").generatePublic(spec);
                } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
                    throw new IllegalArgumentException("Invalid key: " + jwkKey, e);
                }
            }
            case "EC" -> {
                final var x = Base64.getUrlDecoder().decode(jwkKey.x());
                final var y = Base64.getUrlDecoder().decode(jwkKey.y());
                final var pubPoint = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
                try {
                    final var parameters = AlgorithmParameters.getInstance("EC");
                    if (jwkKey.alg().endsWith("256")) {
                        parameters.init(new ECGenParameterSpec("secp256r1"));
                    } else if (jwkKey.alg().endsWith("384")) {
                        parameters.init(new ECGenParameterSpec("secp384r1"));
                    } else {
                        parameters.init(new ECGenParameterSpec("secp521r1"));
                    }
                    final var ecParameters = parameters.getParameterSpec(ECParameterSpec.class);
                    yield KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(pubPoint, ecParameters));
                } catch (final NoSuchAlgorithmException | InvalidParameterSpecException | InvalidKeySpecException e) {
                    throw new IllegalArgumentException("Invalid key: " + jwkKey, e);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported kty='" + jwkKey.kty() + "'");
        };
    }

    @SuppressWarnings("unchecked")
    protected Jwt toJwt(final JwtValidatorConfiguration configuration,
                      final Map<String, Predicate<SigningStringAndSignature>> keyValidators,
                      final SigningStringAndSignature signingStringAndSignature) {
        final var signingString = signingStringAndSignature.signingString();
        final int sep = signingString.indexOf('.');
        if (sep < 0 || signingString.indexOf('.', sep + 1) >= 0) {
            throw new IllegalArgumentException("Invalid jwt header.payload: '" + signingString + "'");
        }

        final var header = signingString.substring(0, sep);
        final var headerData = (Map<String, Object>) mapper.fromBytes(Object.class, Base64.getUrlDecoder().decode(header));
        if (!keyValidators.isEmpty() &&
                requireNonNull(headerData.get("kid"), "missing kid") instanceof String kid &&
                !requireNonNull(keyValidators.get(kid), "unknown kid").test(signingStringAndSignature)) {
            throw new IllegalArgumentException("Invalid JWT '" +
                    signingStringAndSignature.signingString() + '.' + signingStringAndSignature.signature() + "'");
        }

        final var payload = signingString.substring(sep + 1);
        final var payloadData = (Map<String, Object>) mapper.fromBytes(Object.class, Base64.getUrlDecoder().decode(payload));

        final var typ = headerData.get("typ");
        if (!"JWT".equals(typ)) {
            throw new IllegalArgumentException("Invalid JWT typ: '" + typ + "'");
        }

        final var iss = payloadData.get("iss");
        if (configuration.issuer() != null && !Objects.equals(iss, configuration.issuer())) {
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

        final var nbf = payloadData.get("nbf");
        if (nbf instanceof Number number) {
            if (now < 0) {
                now = TimeUnit.MILLISECONDS.toSeconds(clock.millis());
            }
            if (number.longValue() > now + configuration.tolerance()) {
                throw new IllegalArgumentException("nbf after now, invalid JWT");
            }
        } else if (configuration.nbfRequired()) {
            throw new IllegalArgumentException("Missing nbf in JWT");
        }

        return new JwtImpl(mapper, header, payload, headerData, payloadData);
    }

    protected boolean verifyHmac(final String algo, final SecretKey key, final String signingString, final byte[] expected) {
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

    protected boolean verifySignature(final String algo, final PublicKey key, final String signingString, final byte[] expected,
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

    protected record SigningStringAndSignature(String signingString, String signature) {
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
