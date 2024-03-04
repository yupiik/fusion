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
package io.yupiik.fusion.json.template;

public class JsonStringEscaperTemplateProcessor implements StringTemplate.Processor<String, RuntimeException> {
    public static final JsonStringEscaperTemplateProcessor FUSION_JSON = new JsonStringEscaperTemplateProcessor();

    @Override
    public String process(final StringTemplate stringTemplate) {
        final var fragments = stringTemplate.fragments();
        if (fragments.size() == 1) {
            return fragments.get(0);
        }

        final var values = stringTemplate.values();
        final var strings = new String[fragments.size() + values.size()];
        int segmentIt = 0;
        int vIt;
        for (vIt = 0; vIt < values.size(); vIt++) {
            final var previous = fragments.get(vIt);
            final var next = fragments.get(vIt + 1);
            final var value = values.get(vIt);

            final var needsEscaping = value != null &&
                    !previous.isEmpty() && previous.charAt(previous.length() - 1) == '"' &&
                    !next.isEmpty() && next.charAt(0) == '"';

            strings[segmentIt++] = previous;
            if (needsEscaping) {
                strings[segmentIt++] = escape(value.toString());
            } else {
                strings[segmentIt++] = String.valueOf(value);
            }
        }
        strings[segmentIt] = fragments.get(vIt);

        return String.join("", strings);
    }

    private String escape(final String value) { // we don't reuse the codec one yet cause this one avoids the CharBuffer overhead
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (isPassthrough(c)) {
                if (sb != null) {
                    sb.append(c);
                }
                continue;
            }

            if (sb == null) {
                sb = new StringBuilder(value.length() + 4);
                if (i > 0) {
                    sb.append(value, 0, i);
                }
            }
            sb.append(escape(c));
        }
        return sb == null ? value : sb.toString();
    }

    private String escape(final char c) {
        if (isPassthrough(c)) {
            return String.valueOf(c);
        }
        return switch (c) {
            case '"', '\\' -> "\\" + c;
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> {
                final var hex = "000" + Integer.toHexString(c);
                yield "\\u" + hex.substring(hex.length() - 4);
            }
        };
    }

    private static boolean isPassthrough(final char c) {
        return c >= 0x20 && c != 0x22 && c != 0x5c;
    }
}
