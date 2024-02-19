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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class PrivateKeyLoader {
    public PrivateKey loadPrivateKey(final String pem) {
        try {
            return readPEMObjects(pem).stream().map(object -> switch (PEMType.fromBegin(object.beginMarker())) {
                case PRIVATE_KEY_PKCS1 -> rsaPrivateKeyFromPKCS1(object.derBytes());
                case PRIVATE_KEY_PKCS8 -> rsaPrivateKeyFromPKCS8(object.derBytes());
                default -> throw new IllegalArgumentException("Unknown key type: '" + object.beginMarker + "'");
            }).filter(Objects::nonNull).findFirst().orElseGet(() -> {
                if (!pem.startsWith("---")) {
                    return rsaPrivateKeyFromPKCS8(Base64.getDecoder().decode(pem));
                }
                throw new IllegalArgumentException("Invalid key: " + pem);
            });
        } catch (final IOException e) {
            throw new IllegalStateException("Invalid signing", e);
        }
    }

    private PrivateKey rsaPrivateKeyFromPKCS8(final byte[] pkcs8) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    private PrivateKey rsaPrivateKeyFromPKCS1(final byte[] pkcs1) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(newRSAPrivateCrtKeySpec(pkcs1));
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        } catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(e);
        }
    }

    private RSAPrivateCrtKeySpec newRSAPrivateCrtKeySpec(final byte[] keyInPkcs1) throws IOException {
        final var parser = new DerReader(keyInPkcs1);
        final var sequence = parser.read();
        if (sequence.type() != DerReader.SEQUENCE) {
            throw new IllegalArgumentException("Invalid DER: not a sequence");
        }

        final var derReader = sequence.getParser();
        derReader.read(); // version
        return new RSAPrivateCrtKeySpec(derReader.read().getInteger(), derReader.read().getInteger(), derReader.read().getInteger(), derReader.read().getInteger(), derReader.read().getInteger(), derReader.read().getInteger(), derReader.read().getInteger(), derReader.read().getInteger());
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

    private record Asn1Object(int type, int length, byte[] value, int tag) {
        private boolean isConstructed() {
            return (tag & DerReader.CONSTRUCTED) == DerReader.CONSTRUCTED;
        }

        private DerReader getParser() throws IOException {
            if (!isConstructed()) {
                throw new IOException("Invalid DER: can't parse primitive entity");
            }

            return new DerReader(value);
        }

        private BigInteger getInteger() throws IOException {
            if (type != DerReader.INTEGER) {
                throw new IOException("Invalid DER: object is not integer");
            }

            return new BigInteger(value);
        }
    }

    private static class DerReader {
        private final static int CONSTRUCTED = 0x20;
        private final static int INTEGER = 0x02;
        private final static int SEQUENCE = 0x10;

        private final InputStream in;

        private DerReader(final byte[] bytes) {
            in = new ByteArrayInputStream(bytes);
        }

        private Asn1Object read() throws IOException {
            final int tag = in.read();
            if (tag == -1) {
                throw new IOException("Invalid DER: stream too short, missing tag");
            }

            final int length = length();
            final byte[] value = new byte[length];
            final int n = in.read(value);
            if (n < length) {
                throw new IOException("Invalid DER: stream too short, missing value");
            }
            return new Asn1Object(tag & 0x1F, length, value, tag);
        }

        private int length() throws IOException {
            final int i = in.read();
            if (i == -1) {
                throw new IOException("Invalid DER: length missing");
            }
            if ((i & ~0x7F) == 0) {
                return i;
            }
            final int num = i & 0x7F;
            if (i == 0xFF || num > 4) {
                throw new IOException("Invalid DER: length field too big (" + i + ")");
            }
            final var bytes = new byte[num];
            final int n = in.read(bytes);
            if (n < num) {
                throw new IOException("Invalid DER: length too short");
            }
            return new BigInteger(1, bytes).intValue();
        }
    }

    private record PEMObject(String beginMarker, byte[] derBytes) {
    }

    private enum PEMType {
        PRIVATE_KEY_PKCS1("-----BEGIN RSA PRIVATE KEY-----"),
        PRIVATE_EC_KEY_PKCS8("-----BEGIN EC PRIVATE KEY-----"),
        PRIVATE_KEY_PKCS8("-----BEGIN PRIVATE KEY-----");

        private final String beginMarker;

        PEMType(final String beginMarker) {
            this.beginMarker = beginMarker;
        }

        private static PEMType fromBegin(final String beginMarker) {
            return Stream.of(values()).filter(it -> it.beginMarker.equalsIgnoreCase(beginMarker)).findFirst().orElse(null);
        }
    }
}
