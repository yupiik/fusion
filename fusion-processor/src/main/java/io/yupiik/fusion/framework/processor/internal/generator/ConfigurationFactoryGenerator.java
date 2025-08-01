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

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.build.api.configuration.ConfigurationModel;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.fusion.framework.processor.internal.Elements;
import io.yupiik.fusion.framework.processor.internal.meta.Docs;
import io.yupiik.fusion.framework.processor.internal.meta.ReusableDoc;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.ENUM;
import static javax.lang.model.element.ElementKind.RECORD;
import static javax.tools.Diagnostic.Kind.ERROR;

public class ConfigurationFactoryGenerator extends BaseGenerator implements Supplier<ConfigurationFactoryGenerator.Output> {
    static final String SUFFIX = "$FusionConfigurationFactory";

    private final String packageName;
    private final String className;
    private final TypeElement element;
    private final Map<String, Map<String, ReusableDoc>> knownDocs;

    private final Collection<Docs.ClassDoc> docs = new ArrayList<>();
    private final LinkedList<Docs.ClassDoc> docStack = new LinkedList<>();
    private final Map<String, String> enumValueOfCache;

    public ConfigurationFactoryGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                         final String packageName, final String className, final TypeElement element,
                                         final Map<String, String> enumValueOfCache,
                                         final boolean root,
                                         final Map<String, Map<String, ReusableDoc>> knownDocs) {
        super(processingEnv, elements);
        this.packageName = packageName;
        this.className = className;
        this.element = element;
        this.enumValueOfCache = enumValueOfCache;
        this.knownDocs = knownDocs;

        final var doc = new Docs.ClassDoc(root, (packageName.isBlank() ? "" : (packageName + '.')) + className, new ArrayList<>());
        this.docs.add(doc);
        this.docStack.add(doc);
    }

    @Override
    public Output get() {
        if (element.getKind() != RECORD) {
            processingEnv.getMessager().printMessage(ERROR, "No constructor for '" + packageName + '.' + className + "'");
            return new Output(new GeneratedClass("null", "null"), List.of()); // will fail anyway with previous message (-WError)
        }

        final var confClassName = className + SUFFIX;
        final var pckPrefix = packageName.isBlank() ? "" : (packageName + '.');
        final var propPrefix = ofNullable(element.getAnnotation(RootConfiguration.class))
                .map(RootConfiguration::value)
                .filter(Predicate.not(String::isBlank))
                .or(() -> ofNullable(element.getAnnotation(ConfigurationModel.class))
                        .map(it -> ""))
                .orElse(element.getSimpleName().toString());

        final var nestedClasses = new HashMap<String, String>();
        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }
        out.append("public class ").append(confClassName).append(" implements ")
                .append(Supplier.class.getName()).append("<").append(pckPrefix).append(className.replace('$', '.')).append("> {\n");
        out.append("  private final ").append(Configuration.class.getName()).append(" configuration;\n");
        out.append("\n");
        out.append("  public ").append(confClassName).append("(final ").append(Configuration.class.getName()).append(" configuration) {\n");
        out.append("    this.configuration = configuration;\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        out.append("  public ").append(pckPrefix).append(className.replace('$', '.')).append(" get() {\n");
        out.append(createRecordInstance(element, pckPrefix + className.replace('$', '.'), propPrefix, propPrefix, nestedClasses));
        out.append("  }\n");
        if (!nestedClasses.isEmpty()) {
            out.append("\n").append(String.join("\n",
                            nestedClasses.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList()))
                    .append("\n");
        }
        out.append("}\n\n");

        return new Output(new GeneratedClass(pckPrefix + confClassName, out.toString()), docs);
    }

    private String generateNestedClass(final TypeElement element, final String typeName, final String docPrefix, final Map<String, String> nested) {
        final var name = nestedFactory(typeName);
        final var doc = new Docs.ClassDoc(element.getAnnotation(RootConfiguration.class) != null, typeName, new ArrayList<>());
        docStack.add(doc);
        docs.add(doc);
        try {
            return "" +
                    "  private static final class " + name + " implements " + Supplier.class.getName() + "<" + typeName + "> {\n" +
                    "    private final " + Configuration.class.getName() + " configuration;\n" +
                    "    private final String prefix;\n" +
                    "\n" +
                    "    private " + name + "(final " + Configuration.class.getName() + " configuration, final String prefix) {\n" +
                    "      this.configuration = configuration;\n" +
                    "      this.prefix = prefix;\n" +
                    "    }\n" +
                    "\n" +
                    "    @Override\n" +
                    "    public " + typeName + " get() {\n" +
                    createRecordInstance(element, typeName, null, docPrefix, nested).indent(2) +
                    "    }\n" +
                    "\n" +
                    "    private static " + List.class.getName() + "<" + typeName + "> list(final " +
                    Configuration.class.getName() + " configuration, final String prefix, final " + Supplier.class.getName() + "<" + List.class.getName() + "<" + typeName + ">> defaultProvider) {\n" +
                    "        final int length = configuration.get(prefix + \".length\").map(Integer::parseInt).orElse(-1);\n" +
                    "        if (length < 0) {\n" +
                    "          return defaultProvider == null ? null : defaultProvider.get();\n" +
                    "        }\n" +
                    "        final var list = new " + ArrayList.class.getName() + "<" + typeName + ">(length);\n" +
                    "        for (int index = 0; index < length; index++) {\n" +
                    "          list.add(new " + name + "(configuration, prefix + \".\" + index).get());\n" +
                    "        }\n" +
                    "        return list;\n" +
                    "    }\n" +
                    "\n" +
                    "    private static " + Map.class.getName() + "<String, " + typeName + "> map(final " +
                    Configuration.class.getName() + " configuration, final String prefix, final " + Supplier.class.getName() + "<" + Map.class.getName() + "<String, " + typeName + ">> defaultProvider) {\n" +
                    "        final int length = configuration.get(prefix + \".length\").map(Integer::parseInt).orElse(-1);\n" +
                    "        if (length < 0) {\n" +
                    "          return defaultProvider == null ? null : defaultProvider.get();\n" +
                    "        }\n" +
                    "        final var map = new " + LinkedHashMap.class.getName() + "<String, " + typeName + ">(length);\n" +
                    "        for (int index = 0; index < length; index++) {\n" +
                    "          map.put(configuration.get(prefix + \".\" + index + \".key\").orElseThrow(), new " + name + "(configuration, prefix + \".\" + index + \".value\").get());\n" +
                    "        }\n" +
                    "        return map;\n" +
                    "    }\n" +
                    "  }\n";
        } finally {
            docStack.removeLast();
        }
    }

    private String newParamInstance(final Element param, final String propPrefix,
                                    final String parentType,
                                    final String docPrefix,
                                    final Map<String, String> nestedClasses) {
        final var type = param.asType();
        final var typeStr = type.toString();
        final var javaName = param.getSimpleName().toString();
        final var property = ofNullable(param.getAnnotation(Property.class))
                .or(() -> findKnownProperty(parentType, javaName));
        final var selfName = property
                .map(Property::value)
                .filter(Predicate.not(String::isBlank))
                .orElse(javaName);
        final var defaultValue = property
                .map(Property::defaultValue)
                .filter(it -> !Property.NO_VALUE.equals(it))
                .orElse(null);
        final var name = (propPrefix == null ? "prefix + \"" : ("\"" + propPrefix)) + '.' + selfName + "\"";
        final var docName = (docPrefix == null || docPrefix.isBlank() ? "" : (docPrefix + '.')) + selfName;
        final boolean required = property.map(Property::required).orElse(false);
        final var desc = property.map(Property::documentation).orElse("");

        //
        // "primitives" - directly handled types
        //

        if (String.class.getName().equals(typeStr) || CharSequence.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, "", defaultValue != null ? defaultValue : "null", docName, desc);
        }
        if (boolean.class.getName().equals(typeStr) || Boolean.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Boolean::parseBoolean)", defaultValue != null ? defaultValue : "false", docName, desc);
        }
        if (int.class.getName().equals(typeStr) || Integer.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Integer::parseInt)", defaultValue != null ? defaultValue : "0", docName, desc);
        }
        if (long.class.getName().equals(typeStr) || Long.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Long::parseLong)", defaultValue != null ? defaultValue : "0L", docName, desc);
        }
        if (float.class.getName().equals(typeStr) || Float.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Float::parseFloat)", defaultValue != null ? defaultValue : "0.f", docName, desc);
        }
        if (double.class.getName().equals(typeStr) || Double.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Double::parseDouble)", defaultValue != null ? defaultValue : "0.", docName, desc);
        }
        if (BigInteger.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(" + BigInteger.class.getName() + "::new)", defaultValue != null ? defaultValue : "null", docName, desc);
        }
        if (BigDecimal.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(" + BigDecimal.class.getName() + "::new)", defaultValue != null ? defaultValue : "null", docName, desc);
        }
        final var asElement = processingEnv.getTypeUtils().asElement(type);
        if (asElement != null && asElement.getKind() == ENUM) {
            return lookup(javaName, name, required, ".map(" + typeStr + "::" + enumValueOf(asElement) + ")", defaultValue != null ? defaultValue : "null", docName, desc);
        }

        //
        // list
        //

        if (type instanceof DeclaredType dt && dt.getTypeArguments().size() == 1) {
            final var itemType = dt.getTypeArguments().get(0);
            final var itemString = itemType.toString();
            if (String.class.getName().equals(itemString) || CharSequence.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(""), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (boolean.class.getName().equals(itemString) || Boolean.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Boolean::parseBoolean)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (int.class.getName().equals(itemString) || Integer.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Integer::parseInt)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (long.class.getName().equals(itemString) || Long.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Long::parseLong)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (float.class.getName().equals(itemString) || Float.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Float::parseFloat)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (double.class.getName().equals(itemString) || Double.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Double::parseDouble)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (BigInteger.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(" + BigInteger.class.getName() + "::new)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (BigDecimal.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(" + BigDecimal.class.getName() + "::new)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            final var dtElt = processingEnv.getTypeUtils().asElement(itemType);
            if (dtElt != null && dtElt.getKind() == ENUM) {
                return lookup(javaName, name, required, listOf(".map(" + itemString + "::" + enumValueOf(dtElt) + ")"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (itemString.startsWith("java.")) { // unsupported
                processingEnv.getMessager().printMessage(ERROR, "Type not supported: '" + typeStr + "' (" + element + "." + param.getSimpleName() + ")");
                return "null";
            }

            // nested list of objects, here we need to use prefixed notation
            if (!nestedClasses.containsKey(itemString)) {
                nestedClasses.put(itemString, generateNestedClass(
                        (TypeElement) processingEnv.getTypeUtils().asElement(itemType), itemString, null, nestedClasses));
            }

            this.docStack.getLast().items().add(new Docs.DocItem(javaName, docName + ".$index", desc, required, itemString, defaultValue));
            return nestedFactory(itemString) + ".list(configuration, " + name + ", " + (defaultValue == null ? "null" : ("() -> " + defaultValue)) + ")";
        }

        //
        // map<string,x>
        //

        if (type instanceof DeclaredType dt &&
                dt.getTypeArguments().size() == 2 &&
                dt.asElement() instanceof TypeElement te &&
                te.getQualifiedName().contentEquals(Map.class.getName()) &&
                String.class.getName().equals(dt.getTypeArguments().get(0).toString())) {
            final var valueType = dt.getTypeArguments().get(1);
            final var valueTypeString = valueType.toString();
            if (String.class.getName().equals(valueTypeString) || CharSequence.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(""), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (boolean.class.getName().equals(valueTypeString) || Boolean.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Boolean::parseBoolean)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (int.class.getName().equals(valueTypeString) || Integer.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Integer::parseInt)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (long.class.getName().equals(valueTypeString) || Long.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Long::parseLong)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (float.class.getName().equals(valueTypeString) || Float.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Float::parseFloat)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (double.class.getName().equals(valueTypeString) || Double.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Double::parseDouble)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (BigInteger.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(" + BigInteger.class.getName() + "::new)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (BigDecimal.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(" + BigDecimal.class.getName() + "::new)"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            final var dtElt = processingEnv.getTypeUtils().asElement(valueType);
            if (dtElt != null && dtElt.getKind() == ENUM) {
                return lookup(javaName, name, required, mapOf(".map(" + valueTypeString + "::" + enumValueOf(dtElt) + ")"), defaultValue != null ? defaultValue : "null", docName, desc);
            }
            if (valueTypeString.startsWith("java.")) { // unsupported
                processingEnv.getMessager().printMessage(ERROR, "Type not supported: '" + typeStr + "' (" + element + "." + param.getSimpleName() + ")");
                return "null";
            }

            // nested list of objects, here we need to use prefixed notation
            if (!nestedClasses.containsKey(valueTypeString)) {
                nestedClasses.put(valueTypeString, generateNestedClass(
                        (TypeElement) processingEnv.getTypeUtils().asElement(valueType), valueTypeString, null, nestedClasses));
            }

            this.docStack.getLast().items().addAll(List.of(
                new Docs.DocItem(javaName + ".$key", docName + ".$index.key", desc + " (Key).", required, "java.lang.String", "null"),
                new Docs.DocItem(javaName +  ".$value", docName + ".$index.value", desc + " (Value).", required, valueTypeString, "null")));
            return nestedFactory(valueTypeString) + ".map(configuration, " + name + ", " + (defaultValue == null ? "null" : ("() -> " + defaultValue)) + ")";
        }

        if (typeStr.startsWith("java.")) { // unsupported
            processingEnv.getMessager().printMessage(ERROR, "Type not supported: '" + typeStr + "' (" + element + "." + param.getSimpleName() + ")");
            return "null";
        }

        // else assume a nested bean
        if (!nestedClasses.containsKey(typeStr)) {
            nestedClasses.put(typeStr, generateNestedClass(
                    (TypeElement) processingEnv.getTypeUtils().asElement(type), typeStr, null, nestedClasses));
        }

        this.docStack.getLast().items().add(new Docs.DocItem(javaName, docName, desc, required, typeStr, defaultValue));
        if (defaultValue != null) {
            nestedFactory(typeStr); // visit doc
            return defaultValue;
        }
        return "new " + nestedFactory(typeStr) + "(configuration, " + name + ").get()";
    }

    private Optional<Property> findKnownProperty(final String parentType, final String javaName) {
        final var knownType = knownDocs.get(parentType.replace('$', '.'));
        if (knownType == null) {
            return Optional.empty();
        }
        var prop = knownType.get(javaName);
        if (prop == null) { // blind try on list and maps, FIXME: test type before?
            prop = knownType.get(javaName + ".$index");
            if (prop == null) { // blind try on list and maps
                prop = knownType.get(javaName + ".$key");
            }
        }
        if (prop == null) {
            return empty();
        }
        return of(new PropertyImpl(prop));
    }

    private String enumValueOf(final Element asElement) {
        return enumValueOfCache.computeIfAbsent(
                asElement instanceof QualifiedNameable n ? n.getQualifiedName().toString() : asElement.toString(), t -> {
                    // default to valueOf() if there is to fromConfigurationString static method taking a single String parameter
                    if (asElement instanceof TypeElement te && elements.findMethods(te)
                            .anyMatch(it -> it.getSimpleName().contentEquals("fromConfigurationString") &&
                                    it.getParameters().size() == 1 &&
                                    String.class.getName().equals(it.getParameters().get(0).asType().toString()))) {
                        return "fromConfigurationString";
                    }

                    return "valueOf";
                });
    }

    private String lookup(final String javaName,
                          final String name, final boolean required, final String mapper, final String defaultValue,
                          final String docName, final String desc) {
        this.docStack.getLast().items().add(new Docs.DocItem(javaName, docName, desc, required, null, defaultValue));
        return "configuration.get(" + name + ")" +
                mapper +
                (required ?
                        ".orElseThrow(() -> { " +
                                "final var name = " + name + "; " +
                                "return new io.yupiik.fusion.framework.api.configuration.MissingRequiredParameterException(" +
                                "\"No value for '\" + name + \"'\"); })" :
                        ".orElse(" + defaultValue + ")");
    }

    private String listOf(final String valueMapper) {
        // if value starts with sep=x,... then we split on "x" instead of ","
        return ".map(value -> " + Stream.class.getName() + ".of(value.startsWith(\"sep=\") ? value.substring(6).split(value.substring(4, 5)) : value.split(\",\"))" +
                ".map(String::strip)" +
                ".filter(" + Predicate.class.getName() + ".not(String::isBlank))" +
                valueMapper +
                ".toList())";
    }

    private String mapOf(final String valueMapper) {
        // if value starts with sep=x,... then we split on "x" instead of ","
        return ".map(value -> {\n" +
                "            var props = new " + Properties.class.getName() + "();\n" +
                "            try (final var reader = new " + StringReader.class.getName() + "(value)) {\n" +
                "                props.load(reader);\n" +
                "            } catch (Exception e) { /* ignore for now */ }\n" +
                "            return props;\n" +
                "        })\n" +
                "        .map(it -> it.stringPropertyNames().stream()\n" +
                "           .collect(" + Collectors.class.getName() + ".toMap(\n" +
                "             " + Function.class.getName() + ".identity()," +
                "             " + (valueMapper.isEmpty() ? "it::getProperty" : Function.class.getName() + ".identity().andThen(it::getProperty).andThen(" + valueMapper + ")") + ")))";
    }

    private String nestedFactory(final String typeStr) {
        return typeStr.substring(typeStr.lastIndexOf('.') + 1) + "__NestedFactory";
    }

    private String createRecordInstance(final TypeElement element, final String fqn,
                                        final String propPrefix, final String docPrefix,
                                        final Map<String, String> nested) {
        final var name = element.getQualifiedName().toString();
        return selectConstructor(element)
                .map(constructor -> {
                    final var args = constructor.getParameters().stream()
                            .map(it -> newParamInstance(it, propPrefix, name, docPrefix, nested))
                            .collect(joining(",\n      "));
                    return "    return new " + fqn + "(" + args + ");\n";
                })
                .orElseGet(() -> "    return new " + fqn + "();\n"); // cli options don't always have args
    }

    public record Output(GeneratedClass generatedClass, Collection<Docs.ClassDoc> docs) {
    }
}
