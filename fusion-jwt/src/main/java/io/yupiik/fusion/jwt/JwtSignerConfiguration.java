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

@RootConfiguration("jwt-signer") // enforces factory and doc generation
public record JwtSignerConfiguration(
        // PEM of the private RSA key or the secret (hmac).
        @Property(documentation = "Private key.", required = true) String key,

        // JWT algorithm.
        @Property(documentation = "Default JWT `alg` value if no `keys` is set (mainly useful for Hmac case).", defaultValue = "\"RS256\"") String algorithm,

        // JWT expected issuer (iss claim).
        @Property(documentation = "JWT issuer.", required = true) String issuer,

        // JWT header expected kid value.
        @Property(documentation = "KID header kid.", defaultValue = "\"k001\"") String kid,

        // are exp/iat/nbf required or should they be ignored
        @Property(documentation = "Is `exp` (expiry) required.", defaultValue = "true") boolean expRequired,
        @Property(documentation = "Is `exp` is required the validity used in milliseconds.") long expValidity,
        @Property(documentation = "Is `iat` (issued at) required.", defaultValue = "false") boolean iatRequired,
        @Property(documentation = "Is `nbf` (not before) required.", defaultValue = "false") boolean nbfRequired
) {
}
