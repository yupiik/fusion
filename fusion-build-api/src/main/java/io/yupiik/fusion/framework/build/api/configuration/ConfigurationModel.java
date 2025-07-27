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

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Mark a class as being usable in a configuration instance.
 * <p>
 * The only goal is to enforce the generation of a metadata file usable by another module
 * (it is pointless if in the same module than the {@see io.yupiik.fusion.framework.build.api.configuration.RootConfiguration})
 * reuse the default values and documentation while generating its own factory.
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface ConfigurationModel {
}
