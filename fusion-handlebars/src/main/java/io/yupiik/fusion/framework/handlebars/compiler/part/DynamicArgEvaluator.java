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

import io.yupiik.fusion.framework.handlebars.spi.Accessor;

import java.util.Map;

/**
 * Resolves a name (a simple, dotted, `this.` or `../` path) against the current data and the render
 * context frames. Block params (`as |a b|`) shadow data values, like in handlebars.js.
 */
public record DynamicArgEvaluator(String name) implements ArgEvaluator {
    @Override
    public Object eval(final Accessor accessor, final Object current) {
        return eval(accessor, current, null);
    }

    @Override
    public Object eval(final Accessor accessor, final Object current, final Part.RenderContext context) {
        if (".".equals(name) || "this".equals(name)) {
            return current;
        }

        var path = name;
        var depth = 0;
        while (path.startsWith("../")) {
            depth++;
            path = path.substring(3);
        }
        if (depth > 0) {
            var target = context;
            for (var i = 0; i < depth && target != null; i++) {
                target = target.parent();
            }
            if (target == null) { // no frame info (unit tests): fallback to the plain lookup
                return accessor.find(current, path);
            }
            final var param = target.blockParams().get(path);
            if (param != null) {
                return param;
            }
            final var targetAccessor = target.accessor() == null ? accessor : target.accessor();
            return targetAccessor.find(target.data(), path);
        }

        if (path.startsWith("@../")) { // data variable of a parent frame, e.g. @../index / @../../index
            path = path.substring(1); // drop the leading '@' -> "../index"
            var target = context;
            depth = 0;
            while (path.startsWith("../")) {
                depth++;
                path = path.substring(3);
            }
            for (var i = 0; i < depth && target != null; i++) {
                target = target.parent();
            }
            if (target == null) {
                return null;
            }
            final var targetAccessor = target.accessor() == null ? accessor : target.accessor();
            return targetAccessor.find(target.data(), "@" + path);
        }

        if (context != null) { // block params shadow data values
            for (var frame = context; frame != null; frame = frame.parent()) {
                final var param = frame.blockParams().get(name);
                if (param != null) {
                    return param;
                }
            }
        }
        if (name.startsWith("this.")) { // scoped path: {{helper this.path}} vs a data key literally called "this.path"
            return accessor.find(current, name.substring("this.".length()));
        }
        return accessor.find(current, name);
    }
}