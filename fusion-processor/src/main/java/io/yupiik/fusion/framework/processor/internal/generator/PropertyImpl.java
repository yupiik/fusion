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
package io.yupiik.fusion.framework.processor.internal.generator;

import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.processor.internal.meta.ReusableDoc;

import java.lang.annotation.Annotation;

public class PropertyImpl implements Property {
    private final ReusableDoc prop;

    public PropertyImpl(final ReusableDoc prop) {
        this.prop = prop;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Property.class;
    }

    @Override
    public String value() {
        var name = prop.propName();
        if (name.endsWith(".$value")) {
            name = name.substring(0, name.length() - ".$value".length());
        }
        if (name.endsWith(".$key")) {
            name = name.substring(0, name.length() - ".$key".length());
        }
        if (name.endsWith(".$index")) {
            name = name.substring(0, name.length() - ".$index".length());
        }
        return name;
    }

    @Override
    public String defaultValue() {
        return prop.defaultValue() == null ? Property.NO_VALUE : prop.defaultValue();
    }

    @Override
    public boolean required() {
        return prop.required();
    }

    @Override
    public String documentation() {
        return prop.documentation() == null ? "" : prop.documentation();
    }
}
