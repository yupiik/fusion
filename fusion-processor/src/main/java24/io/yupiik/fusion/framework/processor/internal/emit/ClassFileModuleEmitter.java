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
package io.yupiik.fusion.framework.processor.internal.emit;

import javax.annotation.processing.ProcessingEnvironment;
import java.io.IOException;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

// writes the module directly as bytecode, bypassing javac (and an annotation processing round),
// the class layout mimics exactly what javac produces for the source form of ModuleGenerator
public class ClassFileModuleEmitter implements ModuleClassEmitter {
    private static final ClassDesc FUSION_MODULE = ClassDesc.of("io.yupiik.fusion.framework.api.container.FusionModule");
    private static final ClassDesc FUSION_BEAN = ClassDesc.of("io.yupiik.fusion.framework.api.container.FusionBean");
    private static final ClassDesc FUSION_LISTENER = ClassDesc.of("io.yupiik.fusion.framework.api.container.FusionListener");
    private static final ClassDesc STREAM = ClassDesc.of("java.util.stream.Stream");
    private static final MethodTypeDesc STREAM_OF = MethodTypeDesc.ofDescriptor("([Ljava/lang/Object;)Ljava/util/stream/Stream;");
    private static final MethodTypeDesc RETURNS_STREAM = MethodTypeDesc.of(STREAM);

    @Override
    public void emit(final ProcessingEnvironment environment, final String moduleFqn,
                     final List<String> beans, final List<String> listeners) throws IOException {
        final var moduleDesc = ClassDesc.of(moduleFqn);
        final var simpleName = moduleFqn.substring(moduleFqn.lastIndexOf('.') + 1);
        // the class version must match the compilation target, not the build JVM
        final var version = 44 + environment.getSourceVersion().ordinal();
        final var bytes = ClassFile.of().build(moduleDesc, clb -> {
            clb.withVersion(version, 0);
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
            clb.withSuperclass(ConstantDescs.CD_Object);
            clb.withInterfaceSymbols(FUSION_MODULE);
            clb.with(SourceFileAttribute.of(simpleName + ".java"));
            clb.withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, cob -> cob
                    .aload(0)
                    .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                    .return_());
            if (!beans.isEmpty()) {
                streamOf(clb, "beans", FUSION_BEAN, beans.stream().map(ClassFileModuleEmitter::toBinaryName).toList());
            }
            if (!listeners.isEmpty()) {
                streamOf(clb, "listeners", FUSION_LISTENER, listeners);
            }
        });

        try (final var out = environment.getFiler().createClassFile(moduleFqn).openOutputStream()) {
            out.write(bytes);
        }
    }

    // generated nested beans are referenced with a dot in sources (X$FusionJsonCodec.FusionBean)
    private static String toBinaryName(final String sourceName) {
        final var nested = ".FusionBean";
        return sourceName.endsWith(nested) ?
                sourceName.substring(0, sourceName.length() - nested.length()) + "$FusionBean" :
                sourceName;
    }

    private void streamOf(final ClassBuilder clb, final String method, final ClassDesc itemType, final List<String> items) {
        clb.withMethod(method, RETURNS_STREAM, ClassFile.ACC_PUBLIC, mb -> {
            mb.with(SignatureAttribute.of(MethodSignature.parseFrom(
                    "()Ljava/util/stream/Stream<" + itemType.descriptorString().replace(";", "<*>;") + ">;")));
            mb.withCode(cob -> {
                cob.loadConstant(items.size());
                cob.anewarray(itemType);
                int index = 0;
                for (final var item : items) {
                    final var desc = ClassDesc.of(item);
                    cob.dup();
                    cob.loadConstant(index++);
                    cob.new_(desc);
                    cob.dup();
                    cob.invokespecial(desc, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                    cob.aastore();
                }
                cob.invokestatic(STREAM, "of", STREAM_OF, true);
                cob.areturn();
            });
        });
    }
}
