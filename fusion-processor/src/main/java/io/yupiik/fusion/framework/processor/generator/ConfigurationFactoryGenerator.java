package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.build.api.configuration.Property;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.fusion.framework.processor.Elements;
import io.yupiik.fusion.framework.processor.meta.Docs;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.RECORD;
import static javax.tools.Diagnostic.Kind.ERROR;

public class ConfigurationFactoryGenerator extends BaseGenerator implements Supplier<ConfigurationFactoryGenerator.Output> {
    static final String SUFFIX = "$FusionConfigurationFactory";

    private final String packageName;
    private final String className;
    private final TypeElement element;

    private final Collection<Docs.ClassDoc> docs = new ArrayList<>();
    private final LinkedList<Docs.ClassDoc> docStack = new LinkedList<>();

    public ConfigurationFactoryGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                         final String packageName, final String className, final TypeElement element) {
        super(processingEnv, elements);
        this.packageName = packageName;
        this.className = className;
        this.element = element;

        final var doc = new Docs.ClassDoc((packageName.isBlank() ? "" : (packageName + '.')) + className, new ArrayList<>());
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
                .orElse(element.getSimpleName().toString());

        final var nestedClasses = new HashMap<String, String>();
        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }
        out.append("public class ").append(confClassName).append(" implements ")
                .append(Supplier.class.getName()).append("<").append(pckPrefix).append(className).append("> {\n");
        out.append("  private final ").append(Configuration.class.getName()).append(" configuration;\n");
        out.append("\n");
        out.append("  public ").append(confClassName).append("(final ").append(Configuration.class.getName()).append(" configuration) {\n");
        out.append("    this.configuration = configuration;\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        out.append("  public ").append(pckPrefix).append(className).append(" get() {\n");
        out.append(createRecordInstance(element, pckPrefix + className, propPrefix, propPrefix, nestedClasses));
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
        final var doc = new Docs.ClassDoc(typeName, new ArrayList<>());
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
                    Configuration.class.getName() + " configuration, final String prefix) {\n" +
                    "        final int length = configuration.get(prefix + \".length\").map(Integer::parseInt).orElse(0);\n" +
                    "        final var list = new " + ArrayList.class.getName() + "<" + typeName + ">(length);\n" +
                    "        for (int index = 0; index < length; index++) {\n" +
                    "          list.add(new " + name + "(configuration, prefix + \".\" + index).get());\n" +
                    "        }\n" +
                    "        return list;\n" +
                    "    }\n" +
                    "  }\n";
        } finally {
            docStack.removeLast();
        }
    }

    private String newParamInstance(final Element param, final String propPrefix,
                                    final String docPrefix,
                                    final Map<String, String> nestedClasses) {
        final var type = param.asType();
        final var typeStr = type.toString();
        final var property = ofNullable(param.getAnnotation(Property.class));
        final var selfName = property.map(Property::value)
                .filter(Predicate.not(String::isBlank))
                .orElseGet(() -> param.getSimpleName().toString());
        final var name = (propPrefix == null ? "prefix + \"" : ("\"" + propPrefix)) + '.' + selfName + "\"";
        final var docName = (docPrefix == null ? "" : (docPrefix + '.')) + selfName;
        final boolean required = property.map(Property::required).orElse(false);
        final var desc = property.map(Property::documentation).orElse("");

        //
        // "primitives" - directly handled types
        //

        if (String.class.getName().equals(typeStr) || CharSequence.class.getName().equals(typeStr)) {
            return lookup(name, required, "", "null", docName, desc);
        }
        if (boolean.class.getName().equals(typeStr)) {
            return lookup(name, required, ".map(Boolean::parseBoolean)", "false", docName, desc);
        }
        if (int.class.getName().equals(typeStr)) {
            return lookup(name, required, ".map(Integer::parseInt)", "0", docName, desc);
        }
        if (long.class.getName().equals(typeStr)) {
            return lookup(name, required, ".map(Long::parseLong)", "0L", docName, desc);
        }
        if (float.class.getName().equals(typeStr)) {
            return lookup(name, required, ".map(Float::parseFloat)", "0.f", docName, desc);
        }
        if (double.class.getName().equals(typeStr)) {
            return lookup(name, required, ".map(Double::parseDouble)", "0.", docName, desc);
        }
        if (BigInteger.class.getName().equals(typeStr)) {
            return lookup(name, required, ".map(" + BigInteger.class.getName() + "::new)", "null", docName, desc);
        }
        if (BigDecimal.class.getName().equals(typeStr)) {
            return lookup(name, required, ".map(" + BigDecimal.class.getName() + "::new)", "null", docName, desc);
        }

        //
        // list
        //

        if (type instanceof DeclaredType dt && dt.getTypeArguments().size() == 1) {
            final var itemType = dt.getTypeArguments().get(0);
            final var itemString = itemType.toString();
            if (String.class.getName().equals(itemString) || CharSequence.class.getName().equals(itemString)) {
                return lookup(name, required, listOf(""), "null", docName, desc);
            }
            if (boolean.class.getName().equals(itemString)) {
                return lookup(name, required, listOf(".map(Boolean::parseBoolean)"), "null", docName, desc);
            }
            if (int.class.getName().equals(itemString)) {
                return lookup(name, required, listOf(".map(Integer::parseInt)"), "null", docName, desc);
            }
            if (long.class.getName().equals(itemString)) {
                return lookup(name, required, listOf(".map(Long::parseLong)"), "null", docName, desc);
            }
            if (float.class.getName().equals(itemString)) {
                return lookup(name, required, listOf(".map(Float::parseFloat)"), "null", docName, desc);
            }
            if (double.class.getName().equals(itemString)) {
                return lookup(name, required, listOf(".map(Double::parseDouble)"), "null", docName, desc);
            }
            if (BigInteger.class.getName().equals(itemString)) {
                return lookup(name, required, listOf(".map(" + BigInteger.class.getName() + "::new)"), "null", docName, desc);
            }
            if (BigDecimal.class.getName().equals(itemString)) {
                return lookup(name, required, listOf(".map(" + BigDecimal.class.getName() + "::new)"), "null", docName, desc);
            }
            if (itemString.startsWith("java.")) { // unsupported
                processingEnv.getMessager().printMessage(ERROR, "Type not supported: '" + typeStr + "' (" + element + "." + param.getSimpleName() + ")");
                return "null";
            }

            // nested list of objects, here we need to use prefixed annotation
            if (!nestedClasses.containsKey(itemString)) {
                nestedClasses.put(itemString, generateNestedClass(
                        (TypeElement) processingEnv.getTypeUtils().asElement(itemType), itemString, null, nestedClasses));
            }

            this.docStack.getLast().items().add(new Docs.DocItem(docName + ".$index", desc, required, itemString));
            return nestedFactory(itemString) + ".list(configuration, " + name + ")";
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

        this.docStack.getLast().items().add(new Docs.DocItem(docName, desc, required, typeStr));
        return "new " + nestedFactory(typeStr) + "(configuration, " + name + ").get()";
    }

    private String lookup(final String name, final boolean required, final String mapper, final String defaultValue,
                          final String docName, final String desc) {
        this.docStack.getLast().items().add(new Docs.DocItem(docName, desc, required, null));
        return "configuration.get(" + name + ")" +
                mapper +
                (required ? ".orElseThrow(() -> new IllegalArgumentException(\"No value for '\"" + name + "\"'\"))" : ".orElse(" + defaultValue + ")");
    }

    private String listOf(final String valueMapper) {
        return ".map(value -> " + Stream.class.getName() + ".of(value.split(\"\\\\.\"))" +
                ".map(String::strip)" +
                ".filter(" + Predicate.class.getName() + ".not(String::isBlank))" +
                valueMapper +
                ".toList())";
    }

    private String nestedFactory(final String typeStr) {
        return typeStr.substring(typeStr.lastIndexOf('.') + 1) + "__NestedFactory";
    }

    private String createRecordInstance(final TypeElement element, final String fqn,
                                        final String propPrefix, final String docPrefix,
                                        final Map<String, String> nested) {
        return selectConstructor(element)
                .map(constructor -> {
                    final var args = constructor.getParameters().stream()
                            .map(it -> newParamInstance(it, propPrefix, docPrefix, nested))
                            .collect(joining(",\n      "));
                    return "    return new " + fqn + "(" + args + ");\n";
                })
                .orElseGet(() -> {
                    processingEnv.getMessager().printMessage(ERROR, "No constructor for '" + fqn + "'");
                    return "    return new " + fqn + "();\n"; // -Werror should make the compilation fail but this should fail too
                });
    }

    public record Output(GeneratedClass generatedClass, Collection<Docs.ClassDoc> docs) {
    }
}
