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
package io.yupiik.fusion.framework.handlebars.helper;

import java.util.Collection;

/**
 * Handlebars.js-like truthiness: null, false, blank strings, numeric zero and empty collections are falsy.
 * Maps (even empty ones) and any other object are truthy, like in JavaScript.
 */
public final class Truthiness {
    public static final Truthiness INSTANCE = new Truthiness();

    private Truthiness() {
        // single instance, like EmptyPart.INSTANCE
    }

    public boolean isFalsy(final Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean b) {
            return !b;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        if (value instanceof Number n) {
            return n.doubleValue() == 0;
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty();
        }
        return false;
    }
}