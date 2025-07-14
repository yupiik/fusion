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
package io.yupiik.fusion.framework.build.api.kubernetes.crd;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Generally put on an operator it defines the metadata needed to generate the crd.json (equivalent of YAML flavor).
 * It relies on the JSON-Schema generated for the model referenced.
 *
 * TODO: handle versions
 */
@Target(TYPE)
@Retention(SOURCE)
public @interface CustomResourceDefinition {
    /**
     * @return the group of the CRD.
     */
    String group();

    /**
     * @return the name of the CRD.
     */
    String name();

    /**
     * @return the version of the CRD.
     */
    String version() default "v1";

    /**
     * @return namespaced or not.
     */
    boolean namespaced() default true;

    /**
     * @return the spec model ({@see io.yupiik.fusion.framework.build.api.json.JsonModel}).
     */
    Class<?> spec();

    /**
     * @return the status model ({@see io.yupiik.fusion.framework.build.api.json.JsonModel}) if any.
     */
    Class<?> status() default Object.class;

    /**
     * @return shortnames if any.
     */
    String[] shortNames();

    // todo: move to the actual fields - requires to revisit the json model so we do not do it yet
    /**
     * @return selectable JSON-Path.
     */
    String[] selectableFields() default {};

    // todo: move to the actual fields - requires to revisit the json model so we do not do it yet
    /**
     * @return selectable JSON-Path.
     */
    PrinterColumn[] additionalPrinterColumns() default {};

    /**
     * @return a description of the CRD.
     */
    String description();

    @Target({})
    @Retention(SOURCE)
    @interface PrinterColumn {
        String name();
        String type();
        String jsonPath();
    }
}
