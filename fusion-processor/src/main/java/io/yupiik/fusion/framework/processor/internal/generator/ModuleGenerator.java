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

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class ModuleGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String className;
    private final Collection<String> allBeans;
    private final Collection<String> allListeners;
    private final String helpers;

    /** Registrations are direct expressions or sibling stream calls, in stable-ID order. */
    public ModuleGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                           final MetadataContributorRegistry metadataContributorRegistry,
                           final String packageName, final String className,
                           final Collection<String> allBeans, final Collection<String> allListeners, final String helpers) {
        super(processingEnv, elements, metadataContributorRegistry);
        this.packageName = packageName;
        this.className = className;
        this.allBeans = allBeans;
        this.allListeners = allListeners;
        this.helpers = helpers;
    }

    @Override
    public GeneratedClass get() {
        final var out = new StringBuilder(2048);
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }
        out.append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\npublic class ").append(className)
                .append(" implements ")
                .append(FusionModule.class.getName()).append(" {\n");
        registrations(out, "beans", FusionBean.class.getName() + "<?>", allBeans);
        registrations(out, "listeners", FusionListener.class.getName() + "<?>", allListeners);
        out.append(helpers).append("}\n");
        return new GeneratedClass((packageName.isBlank() ? "" : packageName + '.') + className, out.toString());
    }

    private void registrations(final StringBuilder out, final String method, final String type, final Collection<String> registrations) {
        if (registrations.isEmpty()) {
            return;
        }
        // Bound source weight as well as descriptor count: typed expressions can vary considerably in size.
        final var chunks = new ArrayList<List<String>>();
        var chunk = new ArrayList<String>();
        int chars = 0;
        for (final var registration : registrations) {
            if (!chunk.isEmpty() && (chunk.size() == 64 || chars + registration.length() > 8_000)) {
                chunks.add(chunk);
                chunk = new ArrayList<>();
                chars = 0;
            }
            chunk.add(registration);
            chars += registration.length();
        }
        chunks.add(chunk);
        out.append("  @Override\n  public java.util.stream.Stream<").append(type).append("> ").append(method).append("() {\n")
                .append("    final var builder = java.util.stream.Stream.<").append(type).append(">builder();\n");
        if (chunks.size() == 1) {
            chunks.get(0).forEach(value -> out.append("    ").append(value).append('\n'));
        } else {
            for (int index = 0; index < chunks.size(); index++) {
                out.append("    ").append(method).append(index).append("(builder);\n");
            }
        }
        out.append("    return builder.build();\n  }\n");
        if (chunks.size() > 1) {
            for (int index = 0; index < chunks.size(); index++) {
                out.append("  private static void ").append(method).append(index).append("(java.util.stream.Stream.Builder<")
                        .append(type).append("> builder) {\n");
                chunks.get(index).forEach(value -> out.append("    ").append(value).append('\n'));
                out.append("  }\n");
            }
        }
    }
}
