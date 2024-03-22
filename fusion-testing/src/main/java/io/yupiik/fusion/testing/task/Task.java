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

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Run a {@link Task.Supplier} before/after a test method.
 * Enables to use beans to provision/cleanup data state for example or capture data for reports.
 */
@Target(METHOD)
@Retention(RUNTIME)
@Repeatable(Task.List.class)
@ExtendWith(TaskExtension.class)
public @interface Task {
    /**
     * @return
     */
    Phase phase();

    Class<? extends Task.Supplier> value() default Supplier.class;

    Property[] properties() default {};

    enum Phase {
        BEFORE, AFTER
    }

    @Retention(RUNTIME)
    @interface Property {
        String name();

        String value();
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @interface List {
        Task[] value();
    }

    /**
     * @param <T> if implementing {@link Supplier} the result type, mainly for {@link Phase#BEFORE} tasks which can inject
     *            a test parameter decorated with {@link TaskResult}
     *            else if it just runs it can implement {@link Runnable} only and make this {@link Void}.
     */
    interface Supplier<T> extends java.util.function.Supplier<T>, Runnable {
        /**
         * @param properties {@link Task#properties()} values.
         */
        default void init(final Map<String, String> properties) {
            // no-op
        }

        @Override
        default void run() {
            get();
        }

        @Override
        default T get() {
            run();
            return null;
        }
    }
}
