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
package io.yupiik.fusion.framework.build.api.persistence;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target({PARAMETER, FIELD})
@Retention(SOURCE)
public @interface Id {
    /**
     * @return when using multiple times this annotation, enables to sort the fields.
     */
    int order() default 0;

    /**
     * @return it is recommended to use a UUID or equivalent as identifier but when mapping an existing
     * database you can need to synchronize and use {@link java.sql.Statement#getGeneratedKeys} to map the keys.
     * For these cases, set this toggle to true.
     * If your model is a POJO the value is directly set but if it is a record the value will be copied at insert time.
     */
    boolean autoIncremented() default false;
}
