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
package io.yupiik.fusion.framework.processor.internal;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One compiler invocation can contain several independent named-module generation contexts. */
public final class ModuleProcessingEnvironment implements ProcessingEnvironment {
    private final ProcessingEnvironment delegate;
    private final Filer filer;
    private final Elements elements;
    private final String moduleName;

    public ModuleProcessingEnvironment(final ProcessingEnvironment delegate, final ModuleElement module) {
        this.delegate = delegate;
        this.moduleName = module.getQualifiedName().toString();
        this.filer = new ModuleFiler(delegate.getFiler(), moduleName);
        this.elements = new ModuleElements(delegate.getElementUtils(), module);
    }

    public static ProcessingEnvironment unwrap(final ProcessingEnvironment environment) {
        return environment instanceof ModuleProcessingEnvironment scoped ? scoped.delegate : environment;
    }

    public String moduleName() {
        return moduleName;
    }

    public boolean owns(final Element element) {
        final var owner = elements.getModuleOf(element);
        return owner != null && owner.getQualifiedName().contentEquals(moduleName);
    }

    @Override
    public Map<String, String> getOptions() { return delegate.getOptions(); }
    @Override
    public Messager getMessager() { return delegate.getMessager(); }
    @Override
    public Filer getFiler() { return filer; }
    @Override
    public Elements getElementUtils() { return elements; }
    @Override
    public Types getTypeUtils() { return delegate.getTypeUtils(); }
    @Override
    public SourceVersion getSourceVersion() { return delegate.getSourceVersion(); }
    @Override
    public Locale getLocale() { return delegate.getLocale(); }

    private record ModuleFiler(Filer delegate, String module) implements Filer {
        @Override
        public JavaFileObject createSourceFile(final CharSequence name, final Element... origins) throws IOException {
            return delegate.createSourceFile(module + '/' + name, origins);
        }
        @Override
        public JavaFileObject createClassFile(final CharSequence name, final Element... origins) throws IOException {
            return delegate.createClassFile(module + '/' + name, origins);
        }
        @Override
        public FileObject createResource(final JavaFileManager.Location location, final CharSequence pack,
                                         final CharSequence name, final Element... origins) throws IOException {
            return delegate.createResource(location, module + '/' + pack, name, origins);
        }
        @Override
        public FileObject getResource(final JavaFileManager.Location location, final CharSequence pack,
                                      final CharSequence name) throws IOException {
            return delegate.getResource(location, module + '/' + pack, name);
        }
    }

    private record ModuleElements(Elements delegate, ModuleElement module) implements Elements {
        @Override
        public PackageElement getPackageElement(final CharSequence name) {
            final var local = delegate.getPackageElement(module, name);
            return local == null ? delegate.getPackageElement(name) : local;
        }
        @Override
        public TypeElement getTypeElement(final CharSequence name) {
            final var local = delegate.getTypeElement(module, name);
            return local == null ? delegate.getTypeElement(name) : local;
        }
        @Override
        public PackageElement getPackageElement(final ModuleElement owner, final CharSequence name) { return delegate.getPackageElement(owner, name); }
        @Override
        public TypeElement getTypeElement(final ModuleElement owner, final CharSequence name) { return delegate.getTypeElement(owner, name); }
        @Override
        public Set<? extends PackageElement> getAllPackageElements(final CharSequence name) { return delegate.getAllPackageElements(name); }
        @Override
        public Set<? extends TypeElement> getAllTypeElements(final CharSequence name) { return delegate.getAllTypeElements(name); }
        @Override
        public ModuleElement getModuleElement(final CharSequence name) { return delegate.getModuleElement(name); }
        @Override
        public Set<? extends ModuleElement> getAllModuleElements() { return delegate.getAllModuleElements(); }
        @Override
        public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(final AnnotationMirror mirror) { return delegate.getElementValuesWithDefaults(mirror); }
        @Override
        public String getDocComment(final Element element) { return delegate.getDocComment(element); }
        @Override
        public boolean isDeprecated(final Element element) { return delegate.isDeprecated(element); }
        @Override
        public Name getBinaryName(final TypeElement type) { return delegate.getBinaryName(type); }
        @Override
        public PackageElement getPackageOf(final Element element) { return delegate.getPackageOf(element); }
        @Override
        public ModuleElement getModuleOf(final Element element) { return delegate.getModuleOf(element); }
        @Override
        public List<? extends Element> getAllMembers(final TypeElement type) { return delegate.getAllMembers(type); }
        @Override
        public List<? extends AnnotationMirror> getAllAnnotationMirrors(final Element element) { return delegate.getAllAnnotationMirrors(element); }
        @Override
        public boolean hides(final Element hider, final Element hidden) { return delegate.hides(hider, hidden); }
        @Override
        public boolean overrides(final ExecutableElement overrider, final ExecutableElement overridden, final TypeElement type) { return delegate.overrides(overrider, overridden, type); }
        @Override
        public String getConstantExpression(final Object value) { return delegate.getConstantExpression(value); }
        @Override
        public void printElements(final Writer writer, final Element... values) { delegate.printElements(writer, values); }
        @Override
        public Name getName(final CharSequence name) { return delegate.getName(name); }
        @Override
        public boolean isFunctionalInterface(final TypeElement type) { return delegate.isFunctionalInterface(type); }
        @Override
        public Origin getOrigin(final Element element) { return delegate.getOrigin(element); }
        @Override
        public Origin getOrigin(final AnnotatedConstruct element, final AnnotationMirror mirror) { return delegate.getOrigin(element, mirror); }
        @Override
        public Origin getOrigin(final ModuleElement owner, final ModuleElement.Directive directive) { return delegate.getOrigin(owner, directive); }
        @Override
        public boolean isBridge(final ExecutableElement method) { return delegate.isBridge(method); }
        @Override
        public RecordComponentElement recordComponentFor(final ExecutableElement accessor) { return delegate.recordComponentFor(accessor); }
        @Override
        public boolean isAutomaticModule(final ModuleElement owner) { return delegate.isAutomaticModule(owner); }
    }
}
