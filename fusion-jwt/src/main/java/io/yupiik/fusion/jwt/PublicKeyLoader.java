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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class PublicKeyLoader {
    public PublicKey loadPublicKey(final String pem, final String rawAlgo) {
        final var algorithm = switch (rawAlgo) {
            case "RSA", "RS256", "RS384", "RS512", "PS256", "PS384", "PS512" -> "RSA";
            case "EC", "ES256", "ES384", "ES512" -> "EC";
            case "OKP", "EdDSA" -> "EdDSA";
            case "Ed25519", "Ed448" -> rawAlgo;
            default -> throw new IllegalArgumentException("Unsupported key algorithm: " + rawAlgo);
        };
        try {
            return readPEMObjects(pem).stream()
                    .filter(o -> PEMType.fromBegin(o.beginMarker()) == PEMType.PUBLIC_KEY_X509)
                    .map(it -> {
                        try {
                            return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(it.derBytes()));
                        } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> {
                        try { // give a try directly if the x509 was inlined
                            final var keyFactory = KeyFactory.getInstance(algorithm);
                            return keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
                        } catch (final NoSuchAlgorithmException | InvalidKeySpecException ex) {
                            final var illegalArgumentException = new IllegalArgumentException("Invalid key: " + pem);
                            illegalArgumentException.addSuppressed(ex);
                            throw illegalArgumentException;
                        }
                    });
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
