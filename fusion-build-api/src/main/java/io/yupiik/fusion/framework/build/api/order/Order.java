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
package io.yupiik.fusion.framework.build.api.order;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Can be used on:
 * <ul>
 *     <li>A {@link io.yupiik.fusion.framework.build.api.event.OnEvent} parameter to sort the listener in the event chaine.</li>
 *     <li>A {@link io.yupiik.fusion.framework.build.api.scanning.Bean}  (explicit or not) to sort its position in a {@link java.util.Collection} injection if not {@link Comparable}.</li>
 * </ul>
 */
@Target({PARAMETER, TYPE, METHOD})
@Retention(SOURCE)
public @interface Order {
    int value();
}
