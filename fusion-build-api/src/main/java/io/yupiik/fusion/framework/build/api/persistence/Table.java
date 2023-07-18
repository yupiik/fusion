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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

@Target(TYPE)
@Retention(SOURCE)
public @interface Table {
    /**
     * Magic value for embeddable table name.
     */
    String EMBEDDABLE = "_";

    /**
     * If empty the class simple name is used else the name of the table to map.
     * <p>
     * There is a small exception when set to {@code _} on a parameter of a table (same location than a column).
     * This means "embedded" and enables to bypass the record limitation of 255 parameters by splitting the columns in multiple records as a workaround.
     * Note that such record must not contain any @{@link Id} columns, there are only supported as direct parameter of the root record.
     * Only a single nested level is supported as of today since it is really a workaround for record limitation and not a recommended solution.
     * Note that callback methods are only supported at root level, not nested ones.
     * Finally, last limitation of this support is that nested primitives are required (so nested instance must be not null).
     *
     * @return enables to force the table name, if not set it is the simple class name which is used.
     */
    String value() default "";
}
