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

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Locale.ROOT;
import static java.util.Optional.empty;

@ApplicationScoped
public class JwtSignerFactory {
    private final JsonMapper mapper;
    private final Clock clock;

    public JwtSignerFactory(final Optional<JsonMapper> mapper, final Optional<Clock> clock) { // also used by proxies
        this.mapper = mapper == null ? null : mapper.orElseGet(() -> new JsonMapperImpl(List.of(), c -> empty()));
        this.clock = clock == null ? null : clock.orElseGet(Clock::systemUTC);
    }

    public Function<Map<String, Object>, String> newJwtFactory(final JwtSignerConfiguration configuration) {
        final var key = new PrivateKeyLoader().loadPrivateKey(configuration.key());
        final var signer = newSigner(configuration.algorithm(), key);
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
        header.put("alg", configuration.algorithm());
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
        try {
            return switch (jwtAlgorithm.toUpperCase(ROOT)) {
                case "RS256" -> signingString -> sign(signingString, privateKey, "SHA256withRSA", null);
                case "RS384" -> signingString -> sign(signingString, privateKey, "SHA384withRSA", null);
                case "RS512" -> signingString -> sign(signingString, privateKey, "SHA512withRSA", null);
                case "PS256" -> {
                    final var ps256Param = new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, PSSParameterSpec.TRAILER_FIELD_BC);
                    yield signingString -> sign(signingString, privateKey, "RSASSA-PSS",
                            s -> {
                                try {
                                    s.setParameter(ps256Param);
                                } catch (final InvalidAlgorithmParameterException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
                }
                case "PS384" -> {
                    final var ps384Param = new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, PSSParameterSpec.TRAILER_FIELD_BC);
                    yield signingString -> sign(signingString, privateKey, "RSASSA-PSS",
                            s -> {
                                try {
                                    s.setParameter(ps384Param);
                                } catch (final InvalidAlgorithmParameterException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
                }
                case "PS512" -> {
                    final var ps512Param = new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, PSSParameterSpec.TRAILER_FIELD_BC);
                    yield signingString -> sign(signingString, privateKey, "RSASSA-PSS",
                            s -> {
                                try {
                                    s.setParameter(ps512Param);
                                } catch (final InvalidAlgorithmParameterException e) {
                                    throw new IllegalStateException(e);
                                }
                            });
                }
                default -> throw new IllegalArgumentException(jwtAlgorithm);
            };
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] sign(final String signingString, final PrivateKey privateKey,
                        final String signingAlgorithm,
                        final Consumer<Signature> customizer) {
        try {
            final var signature = Signature.getInstance(signingAlgorithm);
            if (customizer != null) {
                customizer.accept(signature);
            }
            signature.initSign(privateKey);
            signature.update(signingString.getBytes(StandardCharsets.UTF_8));
            return signature.sign();
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
