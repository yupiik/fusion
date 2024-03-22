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
package io.yupiik.fusion.testing.task;

import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static io.yupiik.fusion.testing.task.Task.Phase.AFTER;
import static io.yupiik.fusion.testing.task.Task.Phase.BEFORE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class TaskExtensionTest {
    @AfterAll
    void run() {
        assertEquals(1, AfterTask.ran);
    }

    @Test
    @Task(phase = BEFORE, value = BeforeTask.class)
    @Task(phase = AFTER, value = AfterTask.class)
    void run(@TaskResult(BeforeTask.class) final String text) {
        assertEquals("before", text);
        assertEquals(0, AfterTask.ran);
    }

    // @DefaultScoped - should be but in this module we don't process annotations - no processor dep
    public static class BeforeTask implements Task.Supplier<String> {
        @Override
        public String get() {
            return "before";
        }
    }

    // @DefaultScoped
    public static class AfterTask implements Task.Supplier<Void> {
        public static int ran = 0;

        @Override
        public void run() {
            ran++;
        }
    }
}
