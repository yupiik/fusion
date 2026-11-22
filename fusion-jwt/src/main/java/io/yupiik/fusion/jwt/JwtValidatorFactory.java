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

import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.Optional.empty;
import static java.util.stream.Collectors.toMap;

@ApplicationScoped
public class JwtValidatorFactory {
    private final JsonMapper mapper;
    private final Clock clock;
    private final Jws jws = new Jws();

    public JwtValidatorFactory(final Optional<JsonMapper> mapper, final Optional<Clock> clock) { // also used by proxies
        this.mapper = mapper == null ? null : mapper.orElseGet(() -> new JsonMapperImpl(List.of(), c -> empty()));
        this.clock = clock == null ? null : clock.orElseGet(Clock::systemUTC);
    }

    public Function<String, Jwt> newValidator(final JwtValidatorConfiguration configuration) {
        final var decoder = Base64.getUrlDecoder();
        final var hasKeys = !configuration.keys().isEmpty();
        final var keyValidators = configuration.keys().stream()
                .filter(it -> ("sig".equals(it.use()) || it.use() == null) &&
                        ("RSA".equals(it.kty()) || "EC".equals(it.kty()) || "OKP".equals(it.kty())))
                .collect(toMap(JwtValidatorConfiguration.JwkKey::kid, k -> prepareValidator(k, decoder)));
        final Predicate<SigningStringAndSignature> defaultValidator;
        if (hasKeys) {
            defaultValidator = null;
        } else if (configuration.algo() != null && configuration.algo().startsWith("HS")) {
            final var key = new SecretKeySpec(configuration.key().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA" + configuration.algo().substring(2));
            defaultValidator = verifier(configuration.algo(), key, decoder);
        } else if (configuration.algo() != null && !configuration.algo().isBlank()) {
            defaultValidator = prepareValidator(new JwtValidatorConfiguration.JwkKey(
                    "", keyType(configuration.algo()), configuration.algo(), "sig",
                    null, null, List.of(configuration.key()), null, null, null), decoder);
        } else {
            throw new IllegalArgumentException("A JWT verification algorithm or keys must be configured");
        }
        return jwt -> {
            final var split = SigningStringAndSignature.of(jwt);
            if (!hasKeys && !defaultValidator.test(split)) {
                throw new IllegalArgumentException("Invalid JWT '" + jwt + "'");
            }
            // A configured but unusable JWKS must never bypass signature validation.
            if (hasKeys && keyValidators.isEmpty()) {
                throw new IllegalArgumentException("No usable JWT verification keys");
            }
            return toJwt(configuration, keyValidators, split);
        };
    }

    private String keyType(final String algorithm) {
        return switch (algorithm) {
            case "RS256", "RS384", "RS512", "PS256", "PS384", "PS512" -> "RSA";
            case "ES256", "ES384", "ES512" -> "EC";
            case "EdDSA", "Ed25519", "Ed448" -> "OKP";
            default -> throw new IllegalArgumentException("Unsupported JWS algorithm: " + algorithm);
        };
    }

    protected Predicate<SigningStringAndSignature> prepareValidator(final JwtValidatorConfiguration.JwkKey jwkKey, final Base64.Decoder decoder) {
        if (!keyType(jwkKey.alg()).equals(jwkKey.kty())) {
            throw new IllegalArgumentException("Incompatible JWT key type and algorithm");
        }
        return verifier(jwkKey.alg(), loadKey(jwkKey), decoder);
    }

    private Predicate<SigningStringAndSignature> verifier(final String algorithm, final Key key, final Base64.Decoder decoder) {
        return split -> {
            final var header = mapper.fromBytes(Object.class, decoder.decode(split.signingString().substring(0, split.signingString().indexOf('.'))));
            if (!(header instanceof Map<?, ?> values) || !algorithm.equals(values.get("alg"))) {
                return false;
            }
            return jws.verify(algorithm, key, split.signingString().getBytes(StandardCharsets.UTF_8), decoder.decode(split.signature()));
        };
    }

    protected PublicKey loadKey(final JwtValidatorConfiguration.JwkKey jwkKey) {
        if (jwkKey.x5c() != null && !jwkKey.x5c().isEmpty()) { // shortcut for default PEM/base64 X.509 key
            return new PublicKeyLoader().loadPublicKey(jwkKey.x5c().get(0), jwkKey.kty());
        }
        try {
            return switch (jwkKey.kty()) {
                case "RSA" -> KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                        new BigInteger(1, Base64.getUrlDecoder().decode(jwkKey.n())),
                        new BigInteger(1, Base64.getUrlDecoder().decode(jwkKey.e()))));
                case "EC" -> {
                    final var curve = switch (jwkKey.alg()) {
                        case "ES256" -> "P-256";
                        case "ES384" -> "P-384";
                        case "ES512" -> "P-521";
                        default -> throw new IllegalArgumentException("Unsupported EC algorithm: " + jwkKey.alg());
                    };
                    if (jwkKey.crv() != null && !curve.equals(jwkKey.crv())) {
                        throw new IllegalArgumentException("Incompatible EC curve and algorithm");
                    }
                    final var parameters = AlgorithmParameters.getInstance("EC");
                    parameters.init(new ECGenParameterSpec(switch (curve) {
                        case "P-256" -> "secp256r1";
                        case "P-384" -> "secp384r1";
                        default -> "secp521r1";
                    }));
                    final var point = new ECPoint(new BigInteger(1, Base64.getUrlDecoder().decode(jwkKey.x())),
                            new BigInteger(1, Base64.getUrlDecoder().decode(jwkKey.y())));
                    yield KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, parameters.getParameterSpec(ECParameterSpec.class)));
                }
                case "OKP" -> {
                    final var length = switch (jwkKey.crv()) {
                        case "Ed25519" -> 32;
                        case "Ed448" -> 57;
                        default -> throw new IllegalArgumentException("Unsupported OKP curve: " + jwkKey.crv());
                    };
                    if (!"EdDSA".equals(jwkKey.alg()) && !jwkKey.alg().equals(jwkKey.crv())) {
                        throw new IllegalArgumentException("Incompatible OKP curve and algorithm");
                    }
                    final var encoded = Base64.getUrlDecoder().decode(jwkKey.x());
                    if (encoded.length != length) {
                        throw new IllegalArgumentException("Invalid OKP public key length");
                    }
                    final var xOdd = (encoded[length - 1] & 0x80) != 0;
                    encoded[length - 1] &= 0x7f;
                    for (int i = 0; i < length / 2; i++) {
                        final var tmp = encoded[i];
                        encoded[i] = encoded[length - i - 1];
                        encoded[length - i - 1] = tmp;
                    }
                    yield KeyFactory.getInstance(jwkKey.crv()).generatePublic(new EdECPublicKeySpec(
                            new NamedParameterSpec(jwkKey.crv()), new EdECPoint(xOdd, new BigInteger(1, encoded))));
                }
                default -> throw new IllegalArgumentException("Unsupported kty='" + jwkKey.kty() + "'");
            };
        } catch (final GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid verification key", e);
        }
    }

    @SuppressWarnings("unchecked")
    protected Jwt toJwt(final JwtValidatorConfiguration configuration,
                        final Map<String, Predicate<SigningStringAndSignature>> keyValidators,
                        final SigningStringAndSignature signingStringAndSignature) {
        final var signingString = signingStringAndSignature.signingString();
        final int sep = signingString.indexOf('.');
        if (sep < 0 || signingString.substring(sep + 1).contains(".")) {
            throw new IllegalArgumentException("Invalid jwt header.payload: '" + signingString + "'");
        }

        final var header = signingString.substring(0, sep);
        final var headerData = (Map<String, Object>) mapper.fromBytes(Object.class, Base64.getUrlDecoder().decode(header));
        if (!keyValidators.isEmpty()) {
            if (!(headerData.get("kid") instanceof String kid) || !keyValidators.containsKey(kid) ||
                    !keyValidators.get(kid).test(signingStringAndSignature)) {
                throw new IllegalArgumentException("Invalid JWT signature or kid");
            }
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

        if (configuration.jtiRequired() && payloadData.get("jti") == null) {
            throw new IllegalArgumentException("Missing jti in JWT");
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

    protected record SigningStringAndSignature(String signingString, String signature) {
        private static SigningStringAndSignature of(final String jwt) {
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
