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
package io.yupiik.fusion.framework.handlebars.compiler.part;

import io.yupiik.fusion.framework.handlebars.compiler.escaping.Escaper;
import io.yupiik.fusion.framework.handlebars.helper.HelperContext;
import io.yupiik.fusion.framework.handlebars.helper.SafeString;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A bare helper call {@code {{helper}}} (no args): the helper receives the current data as its single argument.
 * The result is HTML-escaped unless the call is a raw triple-stache {@code {{{helper}}}} or returns a
 * {@link SafeString}.
 */
public record ThisHelperPart(Function<HelperContext, Object> helper, boolean escaped) implements Part, Escaper {
    @Override
    public String apply(final RenderContext context, final Object currentData) {
        final var result = helper.apply(new HelperContext(List.of(currentData), Map.of()));
        if (result == null) {
            return "";
        }
        if (result instanceof SafeString s) { // SafeString renders raw even in a double-brace mustache
            return s.value();
        }
        return escaped ? escape(String.valueOf(result)) : String.valueOf(result);
    }
}