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
package io.yupiik.fusion.framework.build.api.configuration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Customize a configuration class.
 */
@Target({
        PARAMETER, // records, standard case
        FIELD // for enums only
})
@Retention(SOURCE)
public @interface Property {
    String NO_VALUE = "io.yupiik.fusion.framework.build.api.configuration.Property.NO_VALUE";

    /**
     * @return name of the property - else the field/member name is used.
     */
    String value() default "";

    /**
     * IMPORTANT: the default value is directly injected as value if none is configured, ensure it is valid java.
     *
     * @return default value to use.
     */
    String defaultValue() default NO_VALUE;

    /**
     * @return {@code true} if it should fail at runtime if the value if missing.
     */
    boolean required() default false;

    /**
     * @return some comment about the property goal/intent/usage.
     */
    String documentation() default "";
}
