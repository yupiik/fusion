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
package io.yupiik.fusion.kubernetes.client.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

// todo: not a real parser (comments and multiline string unsupported for ex), it only handles common formats provided by k8s, todo: implement a lexer/parser
public class LightYamlParser {
    public Object parse(final BufferedReader reader) {
        return doParse(new YamlReader(reader), 0, new LazyList(), this::findValue);
    }

    private Object doParse(final YamlReader reader, final int prefixLength,
                           final LazyList list, final BiFunction<LazyList, Map<String, Object>, Object> resultExtractor) {
        int lineNumber = 0;
        Map<String, Object> object = null;
        try {
            String line;
            while ((line = reader.nextLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                final int firstChar = findNextChar(0, line);
                if (firstChar >= line.length()) {
                    continue;
                }

                final var c = line.charAt(firstChar);
                if (c == '#') {
                    continue;
                }

                if (c == '-') { // collection
                    if (firstChar != prefixLength - 2) {
                        reader.line = line; // re-read it in the enclosing context
                        return resultExtractor.apply(list, object);
                    }

                    final int firstCollectionChar = findNextChar(firstChar + 1, line);
                    if (firstCollectionChar >= line.length()) {
                        throw new IllegalStateException("Invalid collection on line " + lineNumber);
                    }

                    if (list.list == null) {
                        list.list = new ArrayList<>();
                    }

                    final int sep = line.indexOf(':', firstCollectionChar);
                    if (sep > 0) {
                        reader.line = line.substring(0, firstChar) + ' ' + line.substring(firstChar + 1); // reparse the line as an object
                        final var listObject = doParse(reader, prefixLength, list, (l, o) -> o);
                        list.list.add(listObject);
                    } else { // scalar
                        list.list.add(toValue(line, firstCollectionChar));
                    }

                } else if (Character.isJavaIdentifierPart(c)) { // attribute
                    if (firstChar != prefixLength) {
                        reader.line = line; // let caller re-read the line, was not belonging to this parsing
                        return resultExtractor.apply(list, object);
                    }

                    final int sep = line.indexOf(':');
                    if (sep < 0) {
                        throw new IllegalArgumentException("No separator on line " + lineNumber);
                    }

                    if (object == null) {
                        object = new LinkedHashMap<>();
                    }

                    final var key = line.substring(firstChar, sep);
                    final int dataStart = findNextChar(sep + 1, line);
                    if (dataStart == line.length()) { // object start
                        final var nested = doParse(reader, prefixLength + 2, new LazyList(), this::findValue);
                        object.put(key, nested);
                    } else {
                        final var value = toValue(line, dataStart);
                        object.put(key, value);
                    }
                } else {
                    throw new IllegalArgumentException("Unknown char '" + c + "' on line " + lineNumber);
                }
            }

            return resultExtractor.apply(list, object);
        } catch (final IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private Object findValue(final LazyList list, final Map<String, Object> object) {
        if (list != null && list.list != null) {
            if (!list.list.isEmpty() && list.list.get(0) instanceof Map<?,?>) {
                Collections.reverse(list.list); // we stacked it in reverse order so clean it for the final extraction
            }
            return list.list;
        }
        if (object != null) {
            return object;
        }
        return Map.of();
    }

    private Object toValue(final String line, final int dataStart) {
        var value = line.substring(dataStart).strip();
        if ("{}".equals(value) || "{ }".equals(value)) {
            return Map.of();
        }
        if ("[]".equals(value) || "[ ]".equals(value)) {
            return List.of();
        }

        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private int findNextChar(final int from, final String line) {
        int c = from;
        while (c < line.length() && Character.isWhitespace(line.charAt(c))) {
            c++;
        }
        return c;
    }

    private static class YamlReader {
        private final BufferedReader delegate;
        private String line;

        private YamlReader(final BufferedReader delegate) {
            this.delegate = delegate;
        }

        private String nextLine() throws IOException {
            if (line != null) {
                final var l = line;
                line = null;
                return l;
            }
            return delegate.readLine();
        }
    }

    private static class LazyList {
        private List<Object> list;
    }
}
