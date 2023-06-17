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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class PublicKeyLoader {
    public PublicKey loadPublicKey(final String pem, final String rawAlgo) {
        try {
            return readPEMObjects(pem).stream()
                    .filter(o -> PEMType.fromBegin(o.beginMarker()) == PEMType.PUBLIC_KEY_X509)
                    .map(it -> {
                        if (rawAlgo.startsWith("RS")) {
                            try {
                                final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                                final EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(it.derBytes());
                                return keyFactory.generatePublic(publicKeySpec);
                            } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                        if (rawAlgo.startsWith("ES")) {
                            try {
                                return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(it.derBytes()));
                            } catch (final InvalidKeySpecException | NoSuchAlgorithmException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Invalid key: " + pem));
        } catch (final IOException e) {
            throw new IllegalStateException("Invalid signing", e);
        }
    }

    private List<PEMObject> readPEMObjects(final String pem) throws IOException {
        try (final var reader = new BufferedReader(new StringReader(pem))) {
            final var pemContents = new ArrayList<PEMObject>();
            final var sb = new StringBuilder();
            boolean readingContent = false;
            String beginMarker = null;
            String endMarker = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (readingContent) {
                    if (line.contains(endMarker)) {
                        pemContents.add(new PEMObject(beginMarker, Base64.getDecoder().decode(sb.toString())));
                        readingContent = false;
                    } else {
                        sb.append(line.trim());
                    }
                } else {
                    if (line.contains("-----BEGIN ")) {
                        readingContent = true;
                        beginMarker = line.trim();
                        endMarker = beginMarker.replace("BEGIN", "END");
                        sb.setLength(0);
                    }
                }
            }
            return pemContents;
        }
    }

    private record PEMObject(String beginMarker, byte[] derBytes) {
    }

    private enum PEMType {
        PUBLIC_KEY_X509("-----BEGIN PUBLIC KEY-----");

        private final String beginMarker;

        PEMType(final String beginMarker) {
            this.beginMarker = beginMarker;
        }

        private static PEMType fromBegin(final String beginMarker) {
            return Stream.of(values()).filter(it -> it.beginMarker.equalsIgnoreCase(beginMarker)).findFirst().orElse(null);
        }
    }
}
