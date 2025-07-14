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
package test.p.crd;

import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.kubernetes.crd.CustomResourceDefinition;

import java.util.List;

@CustomResourceDefinition(
        group = "test.yupiik.io",
        name = "MyCrd",
        description = "",
        shortNames = "mcrd",
        spec = MyOperator.MySpec.class,
        status = MyOperator.MyStatus.class,
        selectableFields = ".spec.type",
        additionalPrinterColumns = @CustomResourceDefinition.PrinterColumn(name = "Type", jsonPath = ".spec.type", type = "string"))
public class MyOperator {
    @JsonModel
    public record MyStatus(String state) {
    }

    @JsonModel
    public record MySpec(int count, MyNestedSpec nested) {
    }

    @JsonModel
    public record MyNestedSpec(String type, List<String> values) {
    }
}
