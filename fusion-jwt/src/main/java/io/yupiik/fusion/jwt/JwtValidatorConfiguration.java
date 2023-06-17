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

public record JwtValidatorConfiguration(
        // PEM of the public key (RSA/EC) or the secret (hmac).
        String key,

        // JWT algorithm.
        String algo,

        // JWT expected issuer (iss claim).
        String issuer,

        // iat/exp tolerance in seconds.
        long tolerance,

        // are exp/iat required or their absence can be ignored
        boolean expRequired,
        boolean iatRequired
) {
    public JwtValidatorConfiguration {
    }

    public JwtValidatorConfiguration(final String key, final String algo, final String issuer) {
        this(key, algo, issuer, 30, true, false);
    }
}
