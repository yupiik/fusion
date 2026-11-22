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

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Locale.ROOT;
import static java.util.Optional.empty;

@ApplicationScoped
public class JwtSignerFactory {
    private final JsonMapper mapper;
    private final Clock clock;
    private final Jws jws = new Jws();

    public JwtSignerFactory(final Optional<JsonMapper> mapper, final Optional<Clock> clock) { // also used by proxies
        this.mapper = mapper == null ? null : mapper.orElseGet(() -> new JsonMapperImpl(List.of(), c -> empty()));
        this.clock = clock == null ? null : clock.orElseGet(Clock::systemUTC);
    }

    public Function<Map<String, Object>, String> newJwtFactory(final JwtSignerConfiguration configuration) {
        final var algorithm = normalizeAlgorithm(configuration.algorithm());
        final Key key = algorithm.startsWith("HS") ?
                new SecretKeySpec(configuration.key().getBytes(StandardCharsets.UTF_8), "HmacSHA" + algorithm.substring(2)) :
                new PrivateKeyLoader().loadPrivateKey(configuration.key(), switch (algorithm) {
                    case "ES256", "ES384", "ES512" -> "EC";
                    case "EdDSA", "Ed25519", "Ed448" -> "EdDSA";
                    default -> "RSA";
                });
        final var signer = newSigner(algorithm, key);
        final var b64Url = Base64.getUrlEncoder().withoutPadding();

        BiConsumer<Supplier<Instant>, Map<String, Object>> enricher = (c, p) -> {
            p.put("jti", UUID.randomUUID().toString());
            p.put("iss", configuration.issuer());
        };
        if (configuration.expRequired()) {
            enricher = enricher.andThen((c, p) -> p.put("exp", c.get().plusMillis(configuration.expValidity()).getEpochSecond()));
        }
        if (configuration.iatRequired()) {
            enricher = enricher.andThen((c, p) -> p.put("iat", c.get().getEpochSecond()));
        }
        if (configuration.nbfRequired()) {
            enricher = enricher.andThen((c, p) -> p.put("nbf", c.get().getEpochSecond()));
        }

        final var header = new LinkedHashMap<String, Object>();
        header.put("alg", algorithm);
        header.put("typ", "JWT");
        header.put("kid", configuration.kid());
        final var jwtHeader = b64Url.encodeToString(mapper.toBytes(header)) + '.';

        final var payloadEnricher = enricher;
        return payload -> {
            final var finalPayload = new LinkedHashMap<String, Object>();
            payloadEnricher.accept(new Supplier<>() {
                private Instant instant;

                @Override
                public Instant get() {
                    return instant == null ? instant = clock.instant() : instant;
                }
            }, finalPayload);
            // let payload override defaults
            if (payload != null && !payload.isEmpty()) {
                finalPayload.putAll(payload);
            }

            final var signingString = jwtHeader + b64Url.encodeToString(mapper.toBytes(finalPayload));
            // assuming payload contains sub it can be a valid access_token
            return signingString + '.' + b64Url.encodeToString(signer.apply(signingString));
        };
    }

    /**
     * @param jwtAlgorithm the JWT algorithm to use.
     * @param privateKey   the private key to sign the incoming signing string.
     * @return the raw signature (not base64 url encoded nor the full JWT to let you reuse this signing process in other locations).
     */
    public Function<String, byte[]> newSigner(final String jwtAlgorithm, final PrivateKey privateKey) {
        return newSigner(jwtAlgorithm, (Key) privateKey);
    }

    /** Creates a raw signer using an asymmetric private key or an HMAC secret key. */
    public Function<String, byte[]> newSigner(final String jwtAlgorithm, final Key key) {
        final var algorithm = normalizeAlgorithm(jwtAlgorithm);
        return signingString -> jws.sign(algorithm, key, signingString.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeAlgorithm(final String algorithm) {
        final var normalized = algorithm.toUpperCase(ROOT);
        return switch (normalized) {
            case "EDDSA" -> "EdDSA";
            case "ED25519" -> "Ed25519";
            case "ED448" -> "Ed448";
            case "HS256", "HS384", "HS512", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported JWS algorithm: " + algorithm);
        };
    }
}
