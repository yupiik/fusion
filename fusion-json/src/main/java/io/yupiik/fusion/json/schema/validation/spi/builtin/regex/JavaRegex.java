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
package io.yupiik.fusion.json.schema.validation.spi.builtin.regex;

import java.util.function.Predicate;
import java.util.regex.Pattern;

// not 100% a JSON-Schema regex impl but way lighter than any js impl (ECMA 262)
// TIP: use POSIX regex and it should work portably
public class JavaRegex implements Predicate<CharSequence> {

    private final Pattern pattern;

    public JavaRegex(final String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    public boolean test(final CharSequence charSequence) {
        return pattern.matcher(charSequence).find();
    }

    @Override
    public String toString() {
        return "JavaRegex{" + pattern + '}';
    }
}
