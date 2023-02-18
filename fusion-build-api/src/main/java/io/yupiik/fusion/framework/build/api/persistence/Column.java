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
package io.yupiik.fusion.framework.build.api.persistence;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target({PARAMETER, FIELD})
@Retention(SOURCE)
public @interface Column {
    /**
     * @return column name.
     */
    String name() default "";

    /**
     * @return a value mapper type to map the java model to the database storage and vice versa.
     */
    Class<? extends ValueMapper> mapper() default ValueMapper.class;

    /**
     * Enables to serialize/deserialize a column in a custom fashion and not just using the JDBC driver.
     * @param <A> database type.
     * @param <B> java type.
     */
    interface ValueMapper<A, B> {
        /**
         * Maps the java value to the database one.
         * @param javaValue model instance.
         * @return the database representation of {@code javaValue}.
         */
        A toDatabase(B javaValue);

        /**
         * Maps the database value to the model instance.
         * @param databaseValue the database instance.
         * @return the java representation of {@code databaseValue}.
         */
        B toJava(A databaseValue);
    }
}
