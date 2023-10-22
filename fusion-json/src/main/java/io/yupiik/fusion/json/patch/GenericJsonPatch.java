/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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
package io.yupiik.fusion.json.patch;

import io.yupiik.fusion.json.pointer.GenericJsonPointer;

import java.util.List;
import java.util.function.Function;

/**
 * JSON-Patch implementation intended to work with generic types
 * ({@see io.yupiik.fusion.json.internal.codec.ObjectJsonCodec}).
 */
public class GenericJsonPatch implements Function<Object, Object> {
    private final List<Patch> operations;

    public GenericJsonPatch(final List<JsonPatchOperation> operations) {
        this.operations = operations.stream()
                .map(Patch::new)
                .toList();
    }

    @Override
    public Object apply(final Object o) {
        Object current = o;
        for (final var operation : operations) {
            current = doApply(operation, current);
        }
        return current;
    }

    private Object doApply(final Patch op, final Object current) {
        return switch (op.spec.op()) {
            case add -> op.pathPointer.add(current, op.spec.value());
            case remove -> op.pathPointer.remove(current);
            case copy -> op.pathPointer.add(op.fromPointer.remove(current), op.fromPointer.apply(current));
            case move -> op.pathPointer.add(current, op.fromPointer.apply(current));
            case replace -> op.pathPointer.add(op.pathPointer.remove(current), op.spec.value());
            case test -> doTest(op, current);
        };
    }

    private Object doTest(final Patch operation, final Object current) {
        final var toTest = operation.pathPointer.apply(current);
        if (!toTest.equals(operation.spec.value())) {
            throw new IllegalArgumentException("TEST operation failed");
        }
        return current;
    }

    private static class Patch {
        private final JsonPatchOperation spec;
        private final GenericJsonPointer pathPointer;
        private final GenericJsonPointer fromPointer;

        private Patch(final JsonPatchOperation spec) {
            this.spec = spec;
            this.pathPointer = new GenericJsonPointer(spec.path());
            if (spec.op() == JsonPatchOperation.Operation.move || spec.op() == JsonPatchOperation.Operation.copy) {
                this.fromPointer = new GenericJsonPointer(spec.from());
            } else {
                this.fromPointer = null;
            }
        }
    }
}
