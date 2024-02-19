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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrivateKeyLoaderTest {
    @Test
    void loadPrivateKey() {
        final var privateKey = new PrivateKeyLoader().loadPrivateKey(
                "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAJVadEdJh+Gds6RtZZv937FJPS4XdYm3BMSSIiFFZPqeYwQeKiqGkEo65PFdeD7mmmPZo8tiZX43lN9cZiJgygLAGCknPuocSaf0/rpLdi78L+0XRTWIrY0y5tWMnNcD1bmEpWyl5x50FT6JW3etGfFfpQrAHSOkgd2R+V19FwjzAgMBAAECgYAR3hITxoUzWurMh1Xk6o33UfwNZpBmMEypY5N3stXuHEKw5xbuTXjiQyzJKgB3rfOBxzNkN9pNK5hrfEyvsi/tzgwjp9V8ApbmotiYViPLtiST3WILpApbNI6/dP0iM98t29RfXBrRaEWD709CreO5S11FWBkU+2a8+hyYz7GE2QJBALUQulTj5p2QeUDEuqBI+vOwvIOfngHExkt9n8UnHlbdWHCJib2QxHjiAVDb4DHYog5KT28eMT2acFItom9NX88CQQDTKfHMoEMWUS3zTVKRq9pidCGn/eRi33EC1wRlijs0u/t/uKbYdnmTAt1I8AXOe2FZeiQo5YfHSj15TGcNqwmdAkEAlx0m5cJurgHtsIh/2VYPW2Kdcpy8mm1HsaletoQ3ZffF3+Zp9rPjxZ+ZyYo4SmGqnpKWSP7BydAi/fLoJkxFMQJAaDKzaWjPkeyfAwbtroohqiFqFi5Xi158so0NU1mhm4UDNmQUmI3lseBg90PRabFCOVfnDfMtS+7bZMaJt5nllQJAaCcR5CoWgqEIHijv0PK0SjmlVRzU5lwRMMi636E6o/gNxnY9tav+GCK9phuTYyrW6BPtbDJvz2N4hVtyTWZW2Q==");
        assertNotNull(privateKey);
        assertEquals("RSA", privateKey.getAlgorithm());
    }
}
