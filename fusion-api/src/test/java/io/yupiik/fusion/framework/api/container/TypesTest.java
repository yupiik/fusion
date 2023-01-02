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
package io.yupiik.fusion.framework.api.container;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class TypesTest {
    private final Types types = new Types();

    @Test
    void classes() {
        assertTrue(types.isAssignable(Impl.class, Impl.class));
        assertTrue(types.isAssignable(Api.class, Api.class));
        assertTrue(types.isAssignable(Impl.class, Api.class));
        assertFalse(types.isAssignable(Api.class, Impl.class));
    }

    @Test
    void parameterizedType() {
        assertTrue(types.isAssignable(
                new Types.ParameterizedTypeImpl(List.class, Api.class),
                new Types.ParameterizedTypeImpl(List.class, Api.class)));
    }

    public interface Api {
    }

    public static class Impl implements Api {
    }
}
