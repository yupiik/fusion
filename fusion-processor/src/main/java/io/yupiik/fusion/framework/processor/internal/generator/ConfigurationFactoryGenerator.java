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
import io.yupiik.fusion.framework.processor.internal.metadata.MetadataContributorRegistry;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.ENUM;
import static javax.lang.model.element.ElementKind.RECORD;
import static javax.tools.Diagnostic.Kind.ERROR;

public class ConfigurationFactoryGenerator extends BaseGenerator implements Supplier<ConfigurationFactoryGenerator.Output> {
    static final String SUFFIX = "$FusionConfigurationFactory";
    private static final String VALUES = "io.yupiik.fusion.framework.api.configuration.internal.ConfigurationValues";
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\\r\n]|\\\\.)*+\"");
    private static final Pattern DECIMAL_LITERAL = Pattern.compile("[-+]?(?:[0-9][0-9_]*(?:\\.[0-9_]*)?|\\.[0-9][0-9_]*)(?:[eE][-+]?[0-9][0-9_]*)?[fFdDlL]?");

    private final String packageName;
    private final String className;
    private final TypeElement element;
    private final Map<String, Map<String, ReusableDoc>> knownDocs;

    private final Collection<Docs.ClassDoc> docs = new ArrayList<>();
    private final LinkedList<Docs.ClassDoc> docStack = new LinkedList<>();
    private final Map<String, String> enumValueOfCache;
    private final StringBuilder propertyMethods = new StringBuilder();
    private final StringBuilder defaultMethods = new StringBuilder();
    private int propertyIndex;
    private final Set<String> listUsages = new HashSet<>();
    private final Set<String> mapUsages = new HashSet<>();

    public ConfigurationFactoryGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                         final MetadataContributorRegistry metadataContributorRegistry,
                                         final String packageName, final String className, final TypeElement element,
                                         final Map<String, String> enumValueOfCache,
                                         final boolean root,
                                         final Map<String, Map<String, ReusableDoc>> knownDocs) {
        super(processingEnv, elements, metadataContributorRegistry);
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
                // "-" marks a "no prefix" root configuration, lookups then use the plain property names
                .map(it -> "-".equals(it) ? "" : it)
                .or(() -> ofNullable(element.getAnnotation(ConfigurationModel.class))
                        .map(it -> ""))
                .orElse(element.getSimpleName().toString());

        final var nestedClasses = new HashMap<String, NestedFragment>();
        final var body = createRecordInstance(element, element.getQualifiedName().toString(), propPrefix, propPrefix, nestedClasses);
        final var type = element.getQualifiedName().toString();
        final var declaration = new StringBuilder("private static " + type + " $method(final " +
                Configuration.class.getName() + " configuration, final java.util.function.Function<String, String> keyMapper) {\n")
                .append(body).append("}\n");
        nestedClasses.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(it -> declaration.append(it.getValue().render(listUsages.contains(it.getKey()), mapUsages.contains(it.getKey()))));
        declaration.append(propertyMethods);
        declaration.append(defaultMethods);
        final var binding = new Binding(packageName, null,
                "java.util.function.BiFunction<" + Configuration.class.getName() + ", java.util.function.Function<String, String>, " + type + ">",
                "(java.util.function.BiFunction<" + Configuration.class.getName() + ", java.util.function.Function<String, String>, " + type + ">) " + Binding.helperReference(5),
                List.of(new Binding.Method(5, declaration.toString(), "")));
        return new Output(new GeneratedClass(pckPrefix + confClassName, binding), docs);
    }

    private NestedFragment generateNestedClass(final TypeElement element, final String typeName, final String docPrefix, final Map<String, NestedFragment> nested) {
        final var name = nestedFactory(typeName);
        final var doc = new Docs.ClassDoc(element.getAnnotation(RootConfiguration.class) != null, typeName, new ArrayList<>());
        docStack.add(doc);
        docs.add(doc);
        try {
            return new NestedFragment(name, typeName, createRecordInstance(element, typeName, null, docPrefix, nested));
        } finally {
            docStack.removeLast();
        }
    }

    private String newParamInstance(final Element param, final String propPrefix,
                                    final String parentType,
                                    final String docPrefix,
                                    final Map<String, NestedFragment> nestedClasses) {
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
        final var name = propPrefix == null ?
                ("prefix + \"." + selfName + "\"") :
                ("\"" + (propPrefix.isBlank() ? "" : (propPrefix + '.')) + selfName + "\"");
        final var docName = (docPrefix == null || docPrefix.isBlank() ? "" : (docPrefix + '.')) + selfName;
        final boolean required = property.map(Property::required).orElse(false);
        final var desc = property.map(Property::documentation).orElse("");

        //
        // "primitives" - directly handled types
        //

        if (String.class.getName().equals(typeStr) || CharSequence.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, "", defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
        }
        if (boolean.class.getName().equals(typeStr) || Boolean.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Boolean::parseBoolean)", defaultValue != null ? defaultValue : "false", docName, desc, typeStr);
        }
        if (int.class.getName().equals(typeStr) || Integer.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Integer::parseInt)", defaultValue != null ? defaultValue : "0", docName, desc, typeStr);
        }
        if (long.class.getName().equals(typeStr) || Long.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Long::parseLong)", defaultValue != null ? defaultValue : "0L", docName, desc, typeStr);
        }
        if (float.class.getName().equals(typeStr) || Float.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Float::parseFloat)", defaultValue != null ? defaultValue : "0.f", docName, desc, typeStr);
        }
        if (double.class.getName().equals(typeStr) || Double.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(Double::parseDouble)", defaultValue != null ? defaultValue : "0.", docName, desc, typeStr);
        }
        if (BigInteger.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(" + BigInteger.class.getName() + "::new)", defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
        }
        if (BigDecimal.class.getName().equals(typeStr)) {
            return lookup(javaName, name, required, ".map(" + BigDecimal.class.getName() + "::new)", defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
        }
        final var asElement = processingEnv.getTypeUtils().asElement(type);
        if (asElement != null && asElement.getKind() == ENUM) {
            return lookup(javaName, name, required, ".map(" + typeStr + "::" + enumValueOf(asElement) + ")", defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
        }

        //
        // list
        //

        if (type instanceof DeclaredType dt && dt.getTypeArguments().size() == 1) {
            final var itemType = dt.getTypeArguments().get(0);
            final var itemString = itemType.toString();
            if (String.class.getName().equals(itemString) || CharSequence.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(""), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (boolean.class.getName().equals(itemString) || Boolean.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Boolean::parseBoolean)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (int.class.getName().equals(itemString) || Integer.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Integer::parseInt)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (long.class.getName().equals(itemString) || Long.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Long::parseLong)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (float.class.getName().equals(itemString) || Float.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Float::parseFloat)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (double.class.getName().equals(itemString) || Double.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(Double::parseDouble)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (BigInteger.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(" + BigInteger.class.getName() + "::new)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (BigDecimal.class.getName().equals(itemString)) {
                return lookup(javaName, name, required, listOf(".map(" + BigDecimal.class.getName() + "::new)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            final var dtElt = processingEnv.getTypeUtils().asElement(itemType);
            if (dtElt != null && dtElt.getKind() == ENUM) {
                return lookup(javaName, name, required, listOf(".map(" + itemString + "::" + enumValueOf(dtElt) + ")"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
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

            this.docStack.getLast().items().add(new Docs.DocItem(javaName, docName + ".$index", desc, required, itemString, defaultValue, itemString));
            listUsages.add(itemString);
            return indexed(nestedFactory(itemString), "list", typeStr, name, defaultValue, propPrefix);
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
                return lookup(javaName, name, required, mapOf(""), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (boolean.class.getName().equals(valueTypeString) || Boolean.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Boolean::parseBoolean)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (int.class.getName().equals(valueTypeString) || Integer.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Integer::parseInt)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (long.class.getName().equals(valueTypeString) || Long.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Long::parseLong)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (float.class.getName().equals(valueTypeString) || Float.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Float::parseFloat)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (double.class.getName().equals(valueTypeString) || Double.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(Double::parseDouble)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (BigInteger.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(" + BigInteger.class.getName() + "::new)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            if (BigDecimal.class.getName().equals(valueTypeString)) {
                return lookup(javaName, name, required, mapOf(".map(" + BigDecimal.class.getName() + "::new)"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
            }
            final var dtElt = processingEnv.getTypeUtils().asElement(valueType);
            if (dtElt != null && dtElt.getKind() == ENUM) {
                return lookup(javaName, name, required, mapOf(".map(" + valueTypeString + "::" + enumValueOf(dtElt) + ")"), defaultValue != null ? defaultValue : "null", docName, desc, typeStr);
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
                new Docs.DocItem(javaName + ".$key", docName + ".$index.key", desc + " (Key).", required, "java.lang.String", "null", "java.lang.String"),
                new Docs.DocItem(javaName +  ".$value", docName + ".$index.value", desc + " (Value).", required, valueTypeString, "null", valueTypeString)));
            mapUsages.add(valueTypeString);
            return indexed(nestedFactory(valueTypeString), "map", typeStr, name, defaultValue, propPrefix);
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

        this.docStack.getLast().items().add(new Docs.DocItem(javaName, docName, desc, required, typeStr, defaultValue, typeStr));
        if (defaultValue != null) {
            nestedFactory(typeStr); // visit doc
            return appendDefault("$method_property" + propertyIndex++, typeStr, defaultValue, propPrefix == null);
        }
        return nestedFactory(typeStr) + "(configuration, keyMapper, " + name + ")";
    }

    private Optional<Property> findKnownProperty(final String parentType, final String javaName) {
        final var knownType = knownDocs.get(parentType);
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
                          final String docName, final String desc, final String type) {
        this.docStack.getLast().items().add(new Docs.DocItem(javaName, docName, desc, required, null, defaultValue, type));
        final var method = "$method_property" + propertyIndex++;
        final var operation = mapper.startsWith("LIST:") ? "list" : mapper.startsWith("MAP:") ? "map" : "scalar";
        final var valueMapper = "list".equals(operation) ? mapper.substring(5) : "map".equals(operation) ? mapper.substring(4) : mapper;
        final var conversion = sharedConversion(valueMapper, type);
        if (conversion != null) {
            // Lookup and conversion must precede evaluation of the eager default.
            return VALUES + "." + operation + "(configuration, " + name + ", keyMapper, " + required + ", " + VALUES + "." + conversion + ")" +
                    (required ? ".orElseThrow()" : ".orElse(" + appendDefault(method, type, defaultValue, name.startsWith("prefix")) + ")");
        }
        final var boxedType = switch (type) {
            case "boolean" -> "Boolean";
            case "int" -> "Integer";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            default -> type;
        };
        final var defaultCall = required ? null : appendDefault(method, type, defaultValue, name.startsWith("prefix"));
        propertyMethods.append("private static ").append(type).append(' ').append(method)
                .append("(final ").append(Configuration.class.getName())
                .append(" configuration, final java.util.function.Function<String, String> keyMapper, final String prefix) {\n")
                .append("final var raw = ").append(VALUES).append(".raw(configuration, ").append(name).append(", keyMapper, ").append(required).append(");\n");
        propertyMethods.append("final java.util.Optional<").append(boxedType).append("> converted;\n")
                .append("if (raw.isPresent()) {\nfinal var value = raw.get();\n");
        if (mapper.startsWith("LIST:")) {
            propertyMethods.append("final var values = ").append(VALUES).append(".split(value);\n")
                    .append("final var items = new java.util.ArrayList<").append(type.substring(type.indexOf('<') + 1, type.lastIndexOf('>'))).append(">();\n")
                    .append("for (final var item : values) { final var stripped = item.strip(); if (!stripped.isBlank()) { items.add(")
                    .append(convert(mapper.substring(5), "stripped")).append("); } }\n")
                    .append("converted = java.util.Optional.of(items.stream().toList());\n");
        } else if (mapper.startsWith("MAP:")) {
            propertyMethods.append("final var props = ").append(VALUES).append(".properties(value);\n")
                    .append("final var items = new java.util.HashMap<String, ")
                    .append(type.substring(type.indexOf(',') + 1, type.lastIndexOf('>')).strip()).append(">();\n")
                    .append("for (final var key : props.stringPropertyNames()) { items.put(key, java.util.Objects.requireNonNull(")
                    .append(convert(mapper.substring(4), "props.getProperty(key)")).append(")); }\n")
                    .append("converted = java.util.Optional.of(items);\n");
        } else {
            propertyMethods.append("converted = java.util.Optional.ofNullable(").append(convert(mapper, "value")).append(");\n");
        }
        propertyMethods.append("} else { converted = java.util.Optional.empty(); }\nreturn ")
                .append(required ? "converted.orElseThrow()" : "converted.orElse(" + defaultCall + ")").append(";\n}\n");
        return method + "(configuration, keyMapper, " + (name.startsWith("prefix") ? "prefix" : "null") + ")";
    }

    private String sharedConversion(final String mapper, final String type) {
        return switch (mapper) {
            case "" -> type.contains("java.lang.CharSequence") ? "CHAR_SEQUENCE" : "STRING";
            case ".map(Boolean::parseBoolean)" -> "BOOLEAN";
            case ".map(Integer::parseInt)" -> "INTEGER";
            case ".map(Long::parseLong)" -> "LONG";
            case ".map(Float::parseFloat)" -> "FLOAT";
            case ".map(Double::parseDouble)" -> "DOUBLE";
            case ".map(java.math.BigInteger::new)" -> "BIG_INTEGER";
            case ".map(java.math.BigDecimal::new)" -> "BIG_DECIMAL";
            default -> null; // User enums stay statically typed in the holder.
        };
    }

    private String convert(final String mapper, final String value) {
        if (mapper.isEmpty()) {
            return value;
        }
        final var reference = mapper.substring(5, mapper.length() - 1);
        final var separator = reference.indexOf("::");
        final var owner = reference.substring(0, separator);
        final var operation = reference.substring(separator + 2);
        return "new".equals(operation) ? "new " + owner + "(" + value + ")" : owner + "." + operation + "(" + value + ")";
    }

    private String listOf(final String valueMapper) {
        return "LIST:" + valueMapper;
    }

    private String mapOf(final String valueMapper) {
        return "MAP:" + valueMapper;
    }

    private String nestedFactory(final String typeStr) {
        // Encode the full type name to avoid same-simple-name nested models colliding.
        return "$method_nested_" + typeStr.replace("_", "_u").replace(".", "_d").replace("$", "_s");
    }

    private String indexed(final String factory, final String operation, final String type,
                           final String name, final String defaultValue, final String propPrefix) {
        final var method = "$method_property" + propertyIndex++;
        final var defaultCall = appendDefault(method, type, defaultValue == null ? "null" : defaultValue, propPrefix == null);
        propertyMethods.append("private static ").append(type).append(' ').append(method)
                .append("(final ").append(Configuration.class.getName())
                .append(" configuration, final java.util.function.Function<String, String> keyMapper, final String prefix) {\n")
                .append("final int length = ").append(VALUES).append(".length(configuration, ").append(name).append(");\n")
                .append("if (length < 0) { return ").append(defaultCall).append("; }\n")
                .append("return ").append(factory).append('_').append(operation)
                .append("(configuration, keyMapper, ").append(name).append(", length);\n}\n");
        return method + "(configuration, keyMapper, " + (propPrefix == null ? "prefix" : "null") + ")";
    }

    private String appendDefault(final String method, final String type, final String expression, final boolean nested) {
        // Arbitrary expressions retain a separate lexical scope, even when context-free.
        // Unicode escapes are translated before tokenization and can turn quoted-looking
        // text into an arbitrary expression. Keep those expressions in isolated helpers.
        if (Set.of("null", "true", "false").contains(expression) ||
                (STRING_LITERAL.matcher(expression).matches() && !expression.contains("\\u")) ||
                (DECIMAL_LITERAL.matcher(expression).matches() && wideningLiteral(type, expression))) {
            return Set.of("int", "long", "float", "double").contains(type) && !type.equals(literalType(expression)) ?
                    "(" + type + ") (" + expression + ")" : expression;
        }
        // Instance fields preserve Java's own name binding, including this references,
        // lambda/local shadows and flow-scoped patterns, without parsing user code.
        defaultMethods.append("private ").append(type).append(' ').append(method)
                .append("_default() { return ").append(expression).append("; }\n");
        return "new $holder(configuration, keyMapper, " + (nested ? "prefix" : "null") + ")." + method + "_default()";
    }

    private boolean wideningLiteral(final String type, final String expression) {
        // A cast adapts Optional's boxed argument, but must not legalize a default
        // which a typed return statement would reject (for example int = 1.5).
        final char suffix = Character.toLowerCase(expression.charAt(expression.length() - 1));
        final int source = suffix == 'f' ? 2 :
                suffix == 'd' || expression.contains(".") || expression.contains("e") || expression.contains("E") ? 3 :
                        suffix == 'l' ? 1 : 0;
        return switch (type) {
            case "int" -> source == 0;
            case "long" -> source <= 1;
            case "float" -> source <= 2;
            default -> true;
        };
    }

    private String literalType(final String expression) {
        final char suffix = Character.toLowerCase(expression.charAt(expression.length() - 1));
        if (suffix == 'f') {
            return "float";
        }
        if (suffix == 'd' || expression.contains(".") || expression.contains("e") || expression.contains("E")) {
            return "double";
        }
        return suffix == 'l' ? "long" : "int";
    }

    private String createRecordInstance(final TypeElement element, final String fqn,
                                        final String propPrefix, final String docPrefix,
                                        final Map<String, NestedFragment> nested) {
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

    private record NestedFragment(String factoryName, String typeName, String getBody) {
        private String render(final boolean withList, final boolean withMap) {
            final var parameters = "final " + Configuration.class.getName() +
                    " configuration, final java.util.function.Function<String, String> keyMapper, final String prefix";
            final var out = new StringBuilder("private static " + typeName + " " + factoryName + "(" + parameters + ") {\n" + getBody + "}\n");
            if (withList) {
                out.append("private static java.util.List<").append(typeName).append("> ").append(factoryName)
                        .append("_list(").append(parameters).append(", final int length) {\n")
                        .append("final var items = new java.util.ArrayList<").append(typeName).append(">(length);\n")
                        .append("for (int index = 0; index < length; index++) { items.add(").append(factoryName)
                        .append("(configuration, keyMapper, prefix + \".\" + index)); }\nreturn items;\n}\n");
            }
            if (withMap) {
                out.append("private static java.util.Map<String, ").append(typeName).append("> ").append(factoryName)
                        .append("_map(").append(parameters).append(", final int length) {\n")
                        .append("final var items = new java.util.LinkedHashMap<String, ").append(typeName).append(">(length);\n")
                        .append("for (int index = 0; index < length; index++) { items.put(configuration.get(prefix + \".\" + index + \".key\").orElseThrow(), ")
                        .append(factoryName).append("(configuration, keyMapper, prefix + \".\" + index + \".value\")); }\nreturn items;\n}\n");
            }
            return out.toString();
        }
    }
}
