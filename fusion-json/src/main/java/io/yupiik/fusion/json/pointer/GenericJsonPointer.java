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
package io.yupiik.fusion.json.pointer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

// forked from johnzon
public class GenericJsonPointer implements Function<Object, Object> {
    private static final Pattern IS_NUMBER = Pattern.compile("\\d+");

    private final String jsonPointer;
    private final List<String> tokens;

    /**
     * @param raw the pointer as in the RFC.
     */
    public GenericJsonPointer(final String raw) {
        if (raw == null || (!raw.equals("") && !raw.startsWith("/"))) {
            throw new IllegalArgumentException("A non-empty JsonPointer string must begin with a '/'");
        }

        this.jsonPointer = raw;
        this.tokens = Stream.of(raw.split("/", -1))
                .map(it -> it.replace("~1", "/").replace("~0", "~"))
                .toList();
    }

    /**
     * Extract the value for this pointer in {@code src}.
     *
     * @param src the data to read from.
     * @return the value extracted from {@code src}.
     */
    @Override
    public Object apply(final Object src) {
        requireNonNull(src, "src must not be null");
        if (jsonPointer.isEmpty()) {
            return src;
        }

        final var lastIdx = tokens.size() - 1;
        Object jsonValue = src;
        for (int i = 1; i < tokens.size(); i++) {
            jsonValue = find(jsonValue, tokens.get(i), i, lastIdx);
        }
        return jsonValue;
    }

    public Object add(final Object target, final Object value) {
        if (jsonPointer.isEmpty()) {
            return value;
        }

        final var currentPath = new ArrayList<String>();
        currentPath.add("");
        return doAdd(target, value, currentPath, true);
    }

    public Object remove(final Object target) {
        return doRemove(target, 1);
    }

    private Object doRemove(final Object obj, final int pos) {
        if (tokens.size() <= pos) { // unlikely
            return obj;
        }

        final var token = tokens.get(pos);
        if (obj instanceof Map<?, ?> jsonObejct) {
            final var output = new LinkedHashMap<String, Object>();
            for (final var entry : jsonObejct.entrySet()) {
                final boolean matchesToken = token.equals(entry.getKey());
                if (matchesToken && pos == tokens.size() - 1) {
                    continue;
                }
                if (matchesToken) {
                    output.put(entry.getKey().toString(), doRemove(entry.getValue(), pos + 1));
                } else {
                    output.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return output;
        }
        if (obj instanceof List<?> jsonArray) {
            if ("-".equals(token) || IS_NUMBER.matcher(token).matches()) {
                final int arrayIndex = mapArrayIndex(token, jsonArray, false);
                final var output = new ArrayList<>();
                final int jsonArraySize = jsonArray.size();
                for (int i = 0; i < jsonArraySize; i++) {
                    final boolean matchesIndex = i == arrayIndex;
                    if (matchesIndex && pos != tokens.size() - 1) {
                        output.add(doRemove(jsonArray.get(i), pos + 1));
                    } else if (!matchesIndex) {
                        output.add(jsonArray.get(i));
                    }
                }
                return output;
            }
            return jsonArray;
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    private Object doAdd(final Object jsonValue, final Object newValue,
                         final List<String> currentPath, final boolean check) {
        if (jsonValue instanceof Map<?, ?> obj) {
            if (!check) {
                return obj;
            }

            final var output = new LinkedHashMap<String, Object>();
            if (obj.isEmpty() && isPositionToAdd(currentPath)) {
                output.put(tokens.get(tokens.size() - 1), newValue);
            } else {
                final var jsonObject = (Map<String, Object>) obj;
                for (final var entry : jsonObject.entrySet()) {
                    currentPath.add(entry.getKey());
                    output.put(entry.getKey(), doAdd(entry.getValue(), newValue, currentPath, canMatch(currentPath)));
                    currentPath.remove(entry.getKey());
                    if (isPositionToAdd(currentPath)) {
                        output.put(tokens.get(tokens.size() - 1), newValue);
                    }
                }
            }
            return output;
        }
        if (jsonValue instanceof List<?> jsonArray) {
            if (!check) {
                return jsonArray;
            }

            final var output = new ArrayList<>();

            final int arrayIndex;
            if (isPositionToAdd(currentPath)) {
                arrayIndex = mapArrayIndex(tokens.get(tokens.size() - 1), jsonArray, canMatch(currentPath));
            } else {
                arrayIndex = -1;
            }

            final int jsonArraySize = jsonArray.size();
            for (int i = 0; i <= jsonArraySize; i++) {
                if (i == arrayIndex) {
                    output.add(newValue);
                }
                if (i == jsonArraySize) {
                    break;
                }

                final var path = String.valueOf(i);
                currentPath.add(path);
                output.add(doAdd(jsonArray.get(i), newValue, currentPath, canMatch(currentPath)));
                currentPath.remove(path);
            }
            return output;
        }
        return jsonValue;
    }

    private Object find(final Object jsonValue, final String token, final int currentPosition, final int referencePosition) {
        if (jsonValue instanceof Map<?, ?> jsonObject) {
            final var data = jsonObject.get(token);
            if (data != null) {
                return data;
            }
            throw new IllegalStateException("'" + jsonObject + "' contains no value for name '" + token + "'");
        }

        if (jsonValue instanceof List<?> jsonArray) {
            validateArrayIndex(token);
            try {
                final int arrayIndex = mapArrayIndex(token, jsonArray, false);
                return jsonArray.get(arrayIndex);
            } catch (final NumberFormatException e) {
                throw new IllegalStateException("'" + token + "' is no valid array index", e);
            }
        }

        if (currentPosition != referencePosition) {
            return jsonValue;
        }
        throw new IllegalStateException("'" + jsonValue + "' contains no element for '" + token + "'");
    }

    private int mapArrayIndex(final String referenceToken, final List<?> jsonArray, final boolean addOperation) {
        if (addOperation && referenceToken.equals("-")) {
            return jsonArray.size();
        }
        if (!addOperation && referenceToken.equals("-")) {
            final int arrayIndex = jsonArray.size();
            validateArraySize(referenceToken, jsonArray, arrayIndex, jsonArray.size());
            return arrayIndex;
        }

        try {
            final int arrayIndex = Integer.parseInt(referenceToken);
            final int arraySize = addOperation ? jsonArray.size() + 1 : jsonArray.size();
            validateArraySize(referenceToken, jsonArray, arrayIndex, arraySize);
            return arrayIndex;
        } catch (final NumberFormatException e) {
            throw new IllegalStateException("'" + referenceToken + "' is no valid array index", e);
        }
    }

    private void validateArraySize(final String referenceToken, final List<?> jsonArray,
                                   final int arrayIndex, final int arraySize) {

        if (arrayIndex >= arraySize) {
            throw new IllegalStateException("'" + jsonArray + "' contains no element for index " + arrayIndex + " and for '" + referenceToken + "'.");
        }
        if (arrayIndex < 0) {
            throw new IllegalStateException(arrayIndex + " is not a valid index for array '" + jsonArray + "' and for '" + referenceToken + "'.");
        }
    }

    private void validateArrayIndex(final String referenceToken) {
        if (referenceToken.startsWith("-") && referenceToken.length() > 1) {
            throw new IllegalStateException("An array index must not start with '" + referenceToken.charAt(0) + "'");
        }
        if (referenceToken.startsWith("0") && referenceToken.length() > 1) {
            throw new IllegalStateException("An array index must not start with a leading '0'");
        }
    }

    private boolean isPositionToAdd(final List<String> currentPath) {
        return currentPath.size() == tokens.size() - 1 &&
                currentPath.get(currentPath.size() - 1).equals(tokens.get(tokens.size() - 2));
    }

    private boolean canMatch(final List<String> currentPath) {
        return currentPath.size() <= tokens.size() &&
                Objects.equals(currentPath.get(currentPath.size() - 1), tokens.get(currentPath.size() - 1));
    }
}
