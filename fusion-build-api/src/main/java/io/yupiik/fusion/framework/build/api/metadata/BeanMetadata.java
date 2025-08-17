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
package io.yupiik.fusion.framework.build.api.metadata;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Enables to associate metadata to the generated {@code io.yupiik.fusion.framework.api.container.FusionBean}.
 * It can then be used at runtime to change the behavior or interpret it differently.
 *
 * Note that it is forwarded to the {@code io.yupiik.fusion.jsonrpc.impl.JsonRpcMethod} and {@code io.yupiik.fusion.cli.internal.CliCommand} too.
 */
@Retention(SOURCE)
@Target({METHOD, TYPE})
@Repeatable(BeanMetadata.List.class)
public @interface BeanMetadata {
    /**
     * @return the name of the metadata.
     */
    String name();

    /**
     * @return the value of the metadata.
     */
    String value();

    @Retention(SOURCE)
    @Target({METHOD, TYPE})
    @interface List {
        BeanMetadata[] value();
    }
}
