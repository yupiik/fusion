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
import java.util.Map;
import java.util.function.Function;

/**
 * Context passed to a helper.
 * `args` are the evaluated positional arguments (empty when none were written in the template),
 * `hash` the evaluated `key=value` arguments (never null), `blockRenderer` and `inverseRenderer`
 * render the block body (respectively the `{{else}}` body) with a given data - both null for
 * inline or sub-expression calls. Passing `null` as the data to a renderer renders the block with
 * the data the helper was invoked with (like `options.fn(this)` in handlebars.js).
 */
public record HelperContext(List<Object> args,
                            Map<String, Object> hash,
                            BlockRenderer blockRenderer,
                            BlockRenderer inverseRenderer) {
    public HelperContext(final List<Object> args, final Map<String, Object> hash) {
        this(args, hash, null, null);
    }
}