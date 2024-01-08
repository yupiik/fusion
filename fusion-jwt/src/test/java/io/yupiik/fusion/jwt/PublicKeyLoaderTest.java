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

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicKeyLoaderTest {
    @Test
    void loadRsa() {
        final var key = new PublicKeyLoader().loadPublicKey("""
                -----BEGIN PUBLIC KEY-----
                MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCccMAzABy+Alo/rYRlu5UmM85y
                wVzcd0ueclx3WOo7aUoenFHDcASRMZWbzRKtaRAhbIk7+vDey20GnsKvDb+IwHOs
                OojMyinUrcUMSYOG7vQqmkBl1wvvKkmY8exCUo8VZmGsghyT3Gy9nww9WRsg1mDG
                10T+jc2Mnz/3Uck54wIDAQAB
                -----END PUBLIC KEY-----""", "RS256");
        assertNotNull(key);
        assertTrue(key instanceof RSAPublicKey, key.getClass()::getName);

        final var rsaKey = (RSAPublicKey) key;
        assertEquals("RSA", rsaKey.getAlgorithm());
        assertEquals(BigInteger.valueOf(65537), rsaKey.getPublicExponent());
        assertEquals(new BigInteger("109856207784715029969838146483956559009170203798855599261553670993850535450169626067628794776228358722566273330063550653056676268471705004877674489184957326185606837254575244848702851550562586691685088126882732770836347239663946873259023731878292799792489674704569353853324164143297741417645480801612948126179"), rsaKey.getModulus());
    }
}
