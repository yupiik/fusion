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

import io.yupiik.fusion.framework.api.container.Types;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static java.time.Instant.EPOCH;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtSignerFactoryTest {
    @Test
    void roundTrip() {
        final var instant = Clock.fixed(EPOCH, ZoneId.of("UTC"));
        final var signer = new JwtSignerFactory(empty(), of(instant))
                .newJwtFactory(new JwtSignerConfiguration(
                        "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAKUZwRUs3Wb770etrpgRtcHacpy6h7HVjYcypjfrNeU2o1EWYvwOTIcHbmHfteAeCavkkJ3blWLQmRVDj45/4NeY8sNujaungOZNy8cTp/8tLtMjAO/gG4IlOdVBolY3PXwH9B4ctS/flif97jYBrHXX8tF1+JUNfbJ5bc1PvbWpAgMBAAECgYAG0ciC6mZ+wXtBt6/Vgi3CwxYm2SGPu+VrpzDscF+6hwY57DXMeX65uRnbGxV1G2iE3B0JGC/UdA9OrIq6dRfBXwdybvQk7iTnLHoT5yRmjrP3eFb+Zs878KNoT+4DLLWcTMl6VUeNR6NmqIMqkpqO0MH0DBLKDvvQZJYKA5tGsQJBANNYuRk4ruy8Pukj/lj3s9ejZMcJkB6PXZCHZQLupXbZYYkAURb5dqiAWR1EhW3etpqyhp1tfaa5ROoE6UT6g00CQQDH+6/jCYgZEeMgEZJUtsDf2bfnynpZRYXnskEF1bnDDy3YV3dFS4LR1M+MOo/4+xnOoGTBY6S1vrfapXQCslXNAkB2Y/UMU9xpcOos36TTYa601SrW9FxvQhA/rhi/k7/M2+jvPeYu4H+/1GYXJxM3gNL5xZfzCCqjApXAIhAqO8rhAkEAtYWYRm2dcpwQ3Ef22hw0gDvgOW5JlgSMIh5j9Qbloc+CXpAt++EpsosHhRKXInnSGALw0bU/iZS+z6FE5zm2tQJAU81sHijmLWDviNxDnPTRMyRI4me4e26APwAgWBi7JDhHiR9WY2FFFLjIJShT0j4nGu13orCuV0nkdK8G+myWsg==",
                        "RS256",
                        "http://test.yupiik.io/fusion/",
                        "001",
                        true, 60_000L, true, true));
        final var service = new JwtValidatorFactory(empty(), of(instant)).newValidator(new JwtValidatorConfiguration(
                "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQClGcEVLN1m++9Hra6YEbXB2nKcuoex1Y2HMqY36zXlNqNRFmL8DkyHB25h37XgHgmr5JCd25Vi0JkVQ4+Of+DXmPLDbo2rp4DmTcvHE6f/LS7TIwDv4BuCJTnVQaJWNz18B/QeHLUv35Yn/e42Aax11/LRdfiVDX2yeW3NT721qQIDAQAB",
                "RS256", "http://test.yupiik.io/fusion/", 30_000L, true, true, true));

        final var token = signer.apply(Map.of("sub", "romain", "roles", List.of("admin")));
        final var jwt = service.apply(token);
        assertEquals("romain", jwt.claim("sub", String.class).orElseThrow());
        assertEquals("http://test.yupiik.io/fusion/", jwt.claim("iss", String.class).orElseThrow());
        assertEquals(List.of("admin"), jwt.claim("roles", new Types.ParameterizedTypeImpl(List.class, String.class)).orElseThrow());
    }
}
