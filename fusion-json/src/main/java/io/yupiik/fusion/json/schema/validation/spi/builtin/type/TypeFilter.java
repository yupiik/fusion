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
package io.yupiik.fusion.json.schema.validation.spi.builtin.type;

import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Predicate;

public sealed interface TypeFilter extends Predicate<Object> permits TypeFilter.ObjectFilter, TypeFilter.ArrayFilter, TypeFilter.NumberFilter, TypeFilter.BooleanFilter, TypeFilter.StringFilter, TypeFilter.NullFilter {
    TypeFilter OBJECT = new ObjectFilter();
    TypeFilter ARRAY = new ArrayFilter();
    TypeFilter NUMBER = new NumberFilter();
    TypeFilter BOOL = new BooleanFilter();
    TypeFilter STRING = new StringFilter();
    TypeFilter NULL = new NullFilter();

    final class ObjectFilter implements TypeFilter {
        @Override
        public boolean test(final Object o) {
            return o instanceof Map<?, ?>;
        }

        @Override
        public String toString() {
            return "OBJECT";
        }
    }

    final class ArrayFilter implements TypeFilter {
        @Override
        public boolean test(final Object o) {
            return o instanceof Collection<?>;
        }

        @Override
        public String toString() {
            return "ARRAY";
        }
    }

    final class NumberFilter implements TypeFilter {
        @Override
        public boolean test(final Object o) {
            return o instanceof Number;
        }

        @Override
        public String toString() {
            return "NUMBER";
        }
    }

    final class BooleanFilter implements TypeFilter {
        @Override
        public boolean test(final Object o) {
            return o instanceof Boolean;
        }

        @Override
        public String toString() {
            return "BOOLEAN";
        }
    }

    final class StringFilter implements TypeFilter {
        @Override
        public boolean test(final Object o) {
            return o instanceof String;
        }

        @Override
        public String toString() {
            return "STRING";
        }
    }

    final class NullFilter implements TypeFilter {
        @Override
        public boolean test(final Object o) {
            return o == null;
        }

        @Override
        public String toString() {
            return "NULL";
        }
    }
}
