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

import java.util.List;
import java.util.function.Function;

/**
 * Renders a block body ({@code options.fn} in handlebars.js) or an inverse body ({@code options.inverse}).
 * {@link #apply(Object, List)} additionally carries the block params ({@code as |a b|}) values;
 * the single-arg form renders without block params.
 */
public interface BlockRenderer extends Function<Object, String> {
    default String apply(final Object data, final List<Object> blockParams) {
        return apply(data);
    }
}