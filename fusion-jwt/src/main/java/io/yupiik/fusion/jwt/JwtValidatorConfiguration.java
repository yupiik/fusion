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

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.fusion.framework.build.api.json.JsonModel;

import java.util.List;

@RootConfiguration("jwt") // enforces factory and doc generation
public record JwtValidatorConfiguration(
        // PEM of the public key (RSA/EC) or the secret (hmac).
        @Property(documentation = "Default public key to use to validate the incoming JWT if no `keys` is set else `kid` is matched against the `keys` set (mainly useful for Hmac case which can't be in `jwk_uri`).", required = true) String key,

        // JWT algorithm.
        @Property(documentation = "Default JWT `alg` value if no `keys` is set (mainly useful for Hmac case).", defaultValue = "\"RS256\"") String algo,

        // JWT expected issuer (iss claim).
        @Property(documentation = "JWT issuer, validation is ignored if null.") String issuer,

        // iat/exp tolerance in seconds.
        @Property(documentation = "Tolerance for date validation (in seconds).", defaultValue = "30L") long tolerance,

        // are exp/iat/nbf required or their absence can be ignored
        @Property(documentation = "Are `exp` (expiry) validation required of can it be skipped if claim is missing.", defaultValue = "true") boolean expRequired,
        @Property(documentation = "Are `iat` (issued at) validation required of can it be skipped if claim is missing.", defaultValue = "false") boolean iatRequired,
        @Property(documentation = "Are `nbf` (not before) validation required of can it be skipped if claim is missing.", defaultValue = "false") boolean nbfRequired,

        // if data come from a jwks_uri (openid connect)
        @Property(documentation = "List of known kids.", defaultValue = "java.util.List.of()") List<JwkKey> keys
) {
    public JwtValidatorConfiguration {
        // no-op
    }

    public JwtValidatorConfiguration(final String key, final String algo, final String issuer,
                                     final long tolerance, final boolean expRequired, final boolean iatRequired, final boolean nbfRequired) {
        this(key, algo, issuer, tolerance, expRequired, iatRequired, nbfRequired, List.of());
    }

    public JwtValidatorConfiguration(final String key, final String algo, final String issuer) {
        this(key, algo, issuer, 30, true, false, false, List.of());
    }

    @JsonModel
    public record JwkKey(String kid,
                         String kty,
                         String alg,
                         String use, // we only need sig ones
                         // RSA
                         String n,
                         String e,
                         List<String> x5c,
                         // EC
                         String crv,
                         String x,
                         String y) {
    }
}
