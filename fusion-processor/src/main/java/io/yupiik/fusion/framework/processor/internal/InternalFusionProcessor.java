/*
 * Copyright (c) 2022-2023 - Yupiik SAS - https://www.yupiik.com
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

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.api.container.context.subclass.DelegatingContext;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.build.api.cli.Command;
import io.yupiik.fusion.framework.build.api.configuration.RootConfiguration;
import io.yupiik.fusion.framework.build.api.container.LazyContext;
import io.yupiik.fusion.framework.build.api.event.OnEvent;
import io.yupiik.fusion.framework.build.api.http.HttpMatcher;
import io.yupiik.fusion.framework.build.api.json.JsonModel;
import io.yupiik.fusion.framework.build.api.json.JsonOthers;
import io.yupiik.fusion.framework.build.api.json.JsonProperty;
import io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc;
import io.yupiik.fusion.framework.build.api.lifecycle.Destroy;
import io.yupiik.fusion.framework.build.api.lifecycle.Init;
import io.yupiik.fusion.framework.build.api.order.Order;
import io.yupiik.fusion.framework.build.api.scanning.Injection;
import io.yupiik.fusion.framework.processor.internal.Bean.FieldInjection;
import io.yupiik.fusion.framework.processor.internal.generator.BaseGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.BeanConfigurationGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.BeanGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.CliCommandGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.ConfigurationFactoryGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.HttpEndpointGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.JsonCodecBeanGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.JsonCodecEnumBeanGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.JsonCodecGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.JsonRpcEndpointGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.ListenerGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.MethodBeanGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.ModuleGenerator;
import io.yupiik.fusion.framework.processor.internal.generator.SubclassGenerator;
import io.yupiik.fusion.framework.processor.internal.json.JsonStrings;
import io.yupiik.fusion.framework.processor.internal.meta.Docs;
import io.yupiik.fusion.framework.processor.internal.meta.JsonSchema;
import io.yupiik.fusion.framework.processor.internal.meta.PartialOpenRPC;
import io.yupiik.fusion.framework.processor.internal.meta.renderer.doc.DocJsonRenderer;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.list;
import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.ElementKind.ENUM;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.ElementKind.PACKAGE;
import static javax.lang.model.element.ElementKind.RECORD;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

// NOTE: we can support private/package fields for injections/proxying but it requires to modify the
//       CLASS_OUTPUT with asm to add accessors/open visibility (protected pby).
//       -> for now we stick to plain default java but it can be revised later.
@SupportedOptions({
        "fusion.skipNotes", // if false, note messages will be emitted, else they are skipped (default)
        "fusion.generateModule", // toggle to enable/disable the automatic module generation (see moduleFqn)
        "fusion.moduleFqn", // fully qualified name of the generated module if generateModule=true
        "fusion.generateBeanForCliCommands", // if not false all CLI command (@Command) will get a bean
        "fusion.generateBeanForHttpEndpoints", // if not false all endpoints (@HttpMatcher) will get a bean
        "fusion.generateBeanForJsonRpcEndpoints", // if not false all JSON-RPC methods (@JsonRpc) will get a bean
        "fusion.generatePartialOpenRPC", // if not false {schemas:[...],methods:[]} is generated in the location set there or META-INF/fusion/jsonrpc/openrpc.json
        "fusion.generateJsonSchemas", // if not false {schemas:[...]} is generated in the location set there or META-INF/fusion/json/schemas.json
        "fusion.generateBeanForJsonCodec", // if not false a bean will be generated for the JSON codecs and make them available to JsonMapper
        "fusion.generateConfigurationDocMetadata", // if not false it will generate a JSON metadata for configuration, by default in META-INF/fusion/configuration/documentation.json else in the value set to the option
        "fusion.generateBeanForRootConfiguration" // if false @RootConfiguration will not get an automatic bean
})
@SupportedAnnotationTypes({
        // CLI
        "io.yupiik.fusion.framework.build.api.cli.Command",
        // JSON
        "io.yupiik.fusion.framework.build.api.json.JsonModel",
        "io.yupiik.fusion.framework.build.api.json.JsonOthers",
        "io.yupiik.fusion.framework.build.api.json.JsonProperty",
        // HTTP
        "io.yupiik.fusion.framework.build.api.http.HttpMatcher",
        // JSON-RPC
        "io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpc",
        "io.yupiik.fusion.framework.build.api.jsonrpc.JsonRpcParam",
        // CONFIGURATION
        "io.yupiik.fusion.framework.build.api.configuration.RootConfiguration",
        "io.yupiik.fusion.framework.build.api.configuration.Property",
        // framework
        "io.yupiik.fusion.framework.build.api.scanning.Bean",
        "io.yupiik.fusion.framework.build.api.scanning.Injection",
        "io.yupiik.fusion.framework.build.api.order.Order",
        "io.yupiik.fusion.framework.build.api.event.OnEvent",
        // these ones are build API but they are used as Class<?> marker at runtime so in main api module
        "io.yupiik.fusion.framework.api.scope.ApplicationScoped",
        "io.yupiik.fusion.framework.api.scope.DefaultScoped"
})
public class InternalFusionProcessor extends AbstractProcessor {
    private TypeMirror init;
    private TypeMirror destroy;
    private Set<String> knownJsonModels;

    private boolean emitNotes;
    private boolean generateBeansForConfiguration;
    private boolean beanForJsonCodecs;
    private boolean beanForHttpEndpoints;
    private boolean beanForCliCommands;
    private boolean beanForJsonRpcEndpoints;
    private String docsMetadataLocation;
    private String jsonSchemaLocation;
    private String openrpcLocation;

    private Elements elements;

    private final Map<String, Element> jsonModels = new HashMap<>();

    // todo: simplify state management for incremental compilation
    // all* naming is used for state related tracked instances
    private final Collection<String> allBeans = new HashSet<>();
    private final Collection<String> allListeners = new HashSet<>();
    private final Collection<Docs.ClassDoc> allConfigurationsDocs = new HashSet<>();
    private Map<String, GeneratedJsonSchema> allJsonSchemas; // if null don't store them
    private PartialOpenRPC partialOpenRPC; // if null don't store them

    // just for perf
    private final Map<String, String> configurationEnumValueOfCache = new HashMap<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        elements = new Elements(processingEnv);
        init = asTypeElement(Init.class).asType();
        destroy = asTypeElement(Destroy.class).asType();

        emitNotes = !Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("fusion.skipNotes", "true"));
        beanForHttpEndpoints = Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("fusion.generateBeanForHttpEndpoints", "true"));
        beanForCliCommands = Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("fusion.generateBeanForCliCommands", "true"));
        beanForJsonRpcEndpoints = Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("fusion.generateBeanForJsonRpcEndpoints", "true"));
        beanForJsonCodecs = Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("fusion.generateBeanForJsonCodec", "true"));
        generateBeansForConfiguration = Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("fusion.generateBeanForRootConfiguration", "true"));
        docsMetadataLocation = ofNullable(processingEnv.getOptions().getOrDefault("fusion.generateConfigurationDocMetadata", "true"))
                .filter(it -> !"false".equals(it))
                .map(it -> "true".equals(it) ? "META-INF/fusion/configuration/documentation.json" : it)
                .orElse(null);
        jsonSchemaLocation = ofNullable(processingEnv.getOptions().getOrDefault("fusion.generateJsonSchemas", "true"))
                .filter(it -> !"false".equals(it))
                .map(it -> "true".equals(it) ? "META-INF/fusion/json/schemas.json" : it)
                .orElse(null);
        openrpcLocation = ofNullable(processingEnv.getOptions().getOrDefault("fusion.generatePartialOpenRPC", "true"))
                .filter(it -> !"false".equals(it))
                .map(it -> "true".equals(it) ? "META-INF/fusion/jsonrpc/openrpc.json" : it)
                .orElse(null);
        knownJsonModels = findAvailableModules()
                .flatMap(FusionModule::beans)
                // we just use the naming convention to match
                .map(it -> it.type().getTypeName())
                .filter(it -> it.endsWith(JsonCodecGenerator.SUFFIX))
                .map(name -> name.substring(0, name.length() - JsonCodecGenerator.SUFFIX.length()))
                .collect(toSet());

        if (jsonSchemaLocation != null) {
            allJsonSchemas = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        }
        if (openrpcLocation != null) {
            partialOpenRPC = new PartialOpenRPC(new TreeMap<>(String.CASE_INSENSITIVE_ORDER), new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        }
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            generateMetadata();
        } else {
            doProcess(roundEnv);
            generateImplicitModuleIfNeeded();
        }
        return false;
    }

    private void doProcess(final RoundEnvironment roundEnv) {
        // find CLI commands
        final var cliCommands = roundEnv.getElementsAnnotatedWith(Command.class).stream()
                .filter(it -> (it.getKind() == CLASS || it.getKind() == RECORD) && it instanceof TypeElement)
                .map(TypeElement.class::cast)
                .toList();

        // find http endpoints
        final var httpEndpoints = roundEnv.getElementsAnnotatedWith(HttpMatcher.class).stream()
                .filter(it -> it.getKind() == METHOD && it instanceof ExecutableElement)
                .map(ExecutableElement.class::cast)
                .toList();

        // find JSON-RPC endpoints
        final var jsonRpcEndpoints = roundEnv.getElementsAnnotatedWith(JsonRpc.class).stream()
                .filter(it -> it.getKind() == METHOD && it instanceof ExecutableElement)
                .map(ExecutableElement.class::cast)
                .toList();

        // find jsonModels
        final var jsonModels = Stream.concat(
                        roundEnv.getElementsAnnotatedWith(JsonModel.class).stream(),
                        Stream.concat(
                                        roundEnv.getElementsAnnotatedWith(JsonProperty.class).stream(),
                                        roundEnv.getElementsAnnotatedWith(JsonOthers.class).stream())
                                .map(Element::getEnclosingElement) /* constructor */
                                .map(Element::getEnclosingElement) /* record */)
                .distinct()
                .toList();

        // add jsonModels to known ones to ensure we can use incremental compilation (resolution/validation as json model works)
        knownJsonModels.addAll(jsonModels.stream()
                .filter(it -> it.getKind() == RECORD)
                .map(it -> ((TypeElement) it).getQualifiedName().toString())
                .toList());

        // find configurations
        final var configurations = roundEnv.getElementsAnnotatedWith(RootConfiguration.class);

        // find beans created because they have at least an injection
        final var beans = findInjections(roundEnv);

        // add beans without any injection and with @Bean OR a built-in scope OR with an event listener
        final var listeners = roundEnv.getElementsAnnotatedWith(OnEvent.class);
        final var explicitBeans = roundEnv.getElementsAnnotatedWith(io.yupiik.fusion.framework.build.api.scanning.Bean.class);
        beans.putAll(findAllBeans(
                roundEnv, listeners, explicitBeans,
                Stream.concat(httpEndpoints.stream(), jsonRpcEndpoints.stream()).toList())
                .filter(it -> !beans.containsKey(it))
                .collect(toMap(identity(), i -> new ArrayList<>( /* see handleSuperClassesInjections() */))));

        // handle inheritance - parent injections
        // NOTE: requires for now that parent is built in the same cycle, if not we should resolve it at runtime using FusionBean#inject
        beans.forEach((bean, injections) -> handleSuperClassesInjections(beans, bean, injections));

        // generate beans, listeners, ...
        configurations.forEach(this::generateConfigurationFactory);
        jsonModels.forEach(this::generateJsonCodec);
        cliCommands.forEach(this::generateCliCommand);
        httpEndpoints.forEach(this::generateHttpEndpoint);
        jsonRpcEndpoints.forEach(this::generateJsonRpcEndpoint); // after json models to get schemas
        beans.forEach(this::generateBean);
        explicitBeans.stream()
                .filter(it -> it.getKind() == METHOD)
                .map(ExecutableElement.class::cast)
                .peek(m -> {
                    if (m.getModifiers().contains(PRIVATE)) {
                        processingEnv.getMessager().printMessage(ERROR, "Unsupported bean method, should not be private: '" + m.getEnclosingElement() + "." + m + "'");
                    }
                })
                .forEach(this::generateProducerBean);
        listeners.stream()
                .map(it -> (ExecutableElement) it.getEnclosingElement())
                .forEach(this::generateListener);
    }

    private Stream<? extends FusionModule> findAvailableModules() {
        // we want ServiceLoader.load(FusionModule.class).stream() but this one would reload the generated one with incr compile
        // so we do it programmatically - old style
        try {
            final var loader = Thread.currentThread().getContextClassLoader();
            final var res = loader.getResources("META-INF/services/" + FusionModule.class.getName());
            return list(res).stream()
                    .flatMap(it -> {
                        try (final var in = new BufferedReader(new InputStreamReader(it.openStream()))) {
                            return in.lines()
                                    .map(String::strip)
                                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                                    .map(l -> {
                                        try {
                                            final var constructor = loader.loadClass(l)
                                                    .asSubclass(FusionModule.class)
                                                    .getConstructor();
                                            constructor.setAccessible(true);
                                            return constructor.newInstance();
                                        } catch (final NoClassDefFoundError | ClassNotFoundException |
                                                       NoSuchMethodException | InstantiationException |
                                                       IllegalAccessException | InvocationTargetException e) {
                                            return null;
                                        }
                                    })
                                    .filter(Objects::nonNull)
                                    .toList() // materialize before the close
                                    .stream();
                        } catch (final IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .collect(toSet())
                    .stream();
        } catch (final IOException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
            return Stream.empty();
        }
    }

    private void generateMetadata() {
        try {
            generateConfMetadata();
            generateJsonMetadata();
            generateOpenRPCMetadata();
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private void generateJsonMetadata() throws IOException {
        if (jsonSchemaLocation == null || allJsonSchemas == null || allJsonSchemas.isEmpty()) {
            return;
        }

        final var json = processingEnv.getFiler().createResource(CLASS_OUTPUT, "", jsonSchemaLocation);
        try (final var out = json.openWriter()) {
            out.write(allJsonSchemas.entrySet().stream()
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a /* drop state one if duplicated */))
                    .entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> JsonStrings.escape(e.getKey()) + ":" + e.getValue().content().toJson())
                    .collect(joining(",", "{\"schemas\":{", "}}")));
        }

        allJsonSchemas.clear();
        if (emitNotes) {
            processingEnv.getMessager().printMessage(NOTE, "Generated '" + jsonSchemaLocation + "'");
        }
    }

    private void generateOpenRPCMetadata() throws IOException {
        if (openrpcLocation == null || partialOpenRPC == null || (partialOpenRPC.schemas().isEmpty() && partialOpenRPC.methods().isEmpty())) {
            return;
        }

        final var json = processingEnv.getFiler().createResource(CLASS_OUTPUT, "", openrpcLocation);
        try (final var out = json.openWriter()) {
            out.write(partialOpenRPC.toJson());
        }

        partialOpenRPC = null;
        if (emitNotes) {
            processingEnv.getMessager().printMessage(NOTE, "Generated '" + openrpcLocation + "'");
        }
    }

    private void generateConfMetadata() throws IOException {
        if (allConfigurationsDocs.isEmpty() || docsMetadataLocation == null) {
            return;
        }

        final var allDocs = allConfigurationsDocs.stream()
                .distinct() // used in multiple files a conf can be duplicated since key is the source path
                .sorted(comparing(Docs.ClassDoc::name))
                .toList();

        final var metadata = Path.of(docsMetadataLocation);
        if (metadata.isAbsolute()) {
            if (metadata.getParent() != null) {
                Files.createDirectories(metadata.getParent());
            }
            try (final var writer = Files.newBufferedWriter(metadata)) {
                writer.write(new DocJsonRenderer(allDocs).get());
            }
        } else {
            final var target = processingEnv.getFiler().createResource(CLASS_OUTPUT, "", docsMetadataLocation);
            try (final var out = target.openWriter()) {
                out.write(new DocJsonRenderer(allDocs).get());
            }
            if (emitNotes) {
                processingEnv.getMessager().printMessage(NOTE, "Generated '" + docsMetadataLocation + "'");
            }
        }

        allConfigurationsDocs.clear();
    }

    private void handleSuperClassesInjections(final Map<Bean, List<FieldInjection>> beans, final Bean bean, final List<FieldInjection> injections) {
        if (!(bean.enclosing() instanceof TypeElement te)) {
            return;
        }
        final var visited = new HashSet<String>();
        var current = te;
        do {
            final var superclass = current.getSuperclass();
            if (superclass == null) {
                break;
            }
            final var elt = processingEnv.getTypeUtils().asElement(superclass);
            if (!(elt instanceof TypeElement parent)) {
                break;
            }
            current = parent;
            final var name = current.getQualifiedName().toString();
            if (!visited.add(name) || Object.class.getName().equals(name)) {
                break;
            }

            final var found = beans.entrySet().stream()
                    .filter(e -> Objects.equals(e.getKey().name(), name))
                    .findFirst();
            if (found.isEmpty()) {
                processingEnv.getMessager().printMessage(
                        NOTE, "Parent '" + name + "' not found for '" + te.getQualifiedName().toString() + "', injections can be missing.");
                continue;
            }

            injections.addAll(found.map(Map.Entry::getValue).orElse(List.of()));
        } while (true);
    }

    private Stream<Bean> findAllBeans(final RoundEnvironment roundEnv, final Set<? extends Element> listeners,
                                      final Set<? extends Element> explicitBeans, final List<ExecutableElement> methodHolders) {
        return Stream.of(
                        explicitBeans.stream(),
                        methodHolders.stream().map(ExecutableElement::getEnclosingElement),
                        roundEnv.getElementsAnnotatedWith(Order.class).stream(),
                        Stream.of(ApplicationScoped.class, DefaultScoped.class)
                                .flatMap(it -> roundEnv.getElementsAnnotatedWith(it).stream())
                                .filter(it -> it.getAnnotation(RootConfiguration.class) == null),
                        listeners.stream()
                                .map(Element::getEnclosingElement)
                                .peek(it -> {
                                    if (it.getModifiers().contains(PRIVATE)) {
                                        processingEnv.getMessager().printMessage(ERROR, "Unsupported private listener: '" + it.getEnclosingElement() + "." + it + "'");
                                    }

                                    if (it instanceof ExecutableElement ee) {
                                        if (ee.getParameters().size() != 1) {
                                            processingEnv.getMessager().printMessage(ERROR, "Unsupported listener, should get exactly one parameter: '" + it.getEnclosingElement() + "." + it + "'");
                                        }
                                    } else {
                                        processingEnv.getMessager().printMessage(ERROR, "Unsupported listener, should be a method: '" + it.getEnclosingElement() + "." + it + "'");
                                    }
                                }))
                .flatMap(identity())
                .distinct()
                .filter(it -> it instanceof TypeElement)
                .map(it -> (TypeElement) it)
                .map(it -> new Bean(it, it.getQualifiedName().toString()));
    }

    private Map<Bean, List<FieldInjection>> findInjections(final RoundEnvironment roundEnv) {
        return roundEnv.getElementsAnnotatedWith(Injection.class).stream()
                .filter(it -> it.getEnclosingElement() != null && it.getEnclosingElement() instanceof TypeElement)
                .peek(it -> {
                    if (it.getModifiers().contains(PRIVATE)) {
                        processingEnv.getMessager().printMessage(ERROR, "Private fields are not supported yet: " + it);
                    }
                })
                .collect(groupingBy(
                        it -> new Bean(it.getEnclosingElement(), ((TypeElement) it.getEnclosingElement()).getQualifiedName().toString()),
                        mapping(it -> {
                            final var field = it.getSimpleName().toString();
                            final var modifiers = it.getModifiers();
                            if (it instanceof VariableElement ve &&
                                    ve.asType() instanceof DeclaredType dt &&
                                    dt.getTypeArguments().size() == 1) {
                                final var arg = dt.getTypeArguments().get(0);
                                if (dt.toString().startsWith(List.class.getName() + "<")) {
                                    return new FieldInjection(field, arg, true, false, false, modifiers);
                                }
                                if (dt.toString().startsWith(Set.class.getName() + "<")) {
                                    return new FieldInjection(field, arg, false, true, false, modifiers);
                                }
                                if (dt.toString().startsWith(Optional.class.getName() + "<")) {
                                    return new FieldInjection(field, arg, false, false, true, modifiers);
                                }
                            }
                            return new FieldInjection(field, it.asType(), false, false, false, modifiers);
                        }, toList())));
    }

    private void generateJsonCodec(final Element model) {
        if (!(model instanceof TypeElement element) || (element.getKind() != RECORD && element.getKind() != ENUM)) {
            processingEnv.getMessager().printMessage(ERROR, "'" + model + "' not a record.");
            return;
        }

        final var names = ParsedName.of(element);
        try {
            if (element.getKind() == ENUM) {
                if (!beanForJsonCodecs) {
                    return;
                }
                final var generation = new JsonCodecEnumBeanGenerator(
                        processingEnv, elements, names.packageName(), names.className(), element.asType()).get();
                writeGeneratedClass(element, generation);
                allBeans.add(generation.name());
                return;
            }
            final var schemas = allJsonSchemas == null ? null : new HashMap<String, JsonSchema>();
            final var generator = new JsonCodecGenerator(
                    processingEnv, elements, names.packageName(), names.className(), element, knownJsonModels, schemas);
            final var generation = generator.get();
            if (schemas != null && !schemas.isEmpty()) {
                allJsonSchemas.putAll(schemas.entrySet().stream()
                        .collect(toMap(Map.Entry::getKey, e -> new GeneratedJsonSchema(e.getValue()))));
            }
            writeGeneratedClass(model, generation);
            jsonModels.put(generation.name(), element);
            if (beanForJsonCodecs) {
                final var beanName = ParsedName.of(generation.name());
                try {
                    final var beanGen = new JsonCodecBeanGenerator(processingEnv, elements, beanName.packageName(), beanName.className()).get();
                    writeGeneratedClass(element, beanGen);
                    allBeans.add(beanGen.name());
                } catch (final IOException | RuntimeException e) {
                    processingEnv.getMessager().printMessage(ERROR, e.getMessage());
                }
            }
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, '(' + e.getClass().getSimpleName() + ") " + e.getMessage());
        }
    }

    private void generateConfigurationFactory(final Element confElt) {
        if (!(confElt instanceof TypeElement element) || element.getKind() != RECORD) {
            processingEnv.getMessager().printMessage(ERROR, "'" + confElt + "' not a record.");
            return;
        }

        final var names = ParsedName.of(element);
        try {
            final var generation = new ConfigurationFactoryGenerator(
                    processingEnv, elements, names.packageName(), names.className(), element,
                    configurationEnumValueOfCache).get();
            writeGeneratedClass(confElt, generation.generatedClass());
            if (generation.docs() != null && docsMetadataLocation != null) {
                allConfigurationsDocs.addAll(generation.docs());
            }

            if (generateBeansForConfiguration) {
                final var bean = new BeanConfigurationGenerator(processingEnv, elements, names.packageName(), names.className(), element).get();
                writeGeneratedClass(confElt, bean);
                allBeans.add(bean.name());
            }
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private void generateJsonRpcEndpoint(final ExecutableElement method) {
        if (method.getEnclosingElement() == null || method.getEnclosingElement().getKind() != CLASS) {
            processingEnv.getMessager().printMessage(ERROR, "'" + method + "' is not enclosed by a class: '" + method.getEnclosingElement() + "'");
            return;
        }

        final var names = ParsedName.of(method.getEnclosingElement());
        try {
            final var generation = new JsonRpcEndpointGenerator(
                    processingEnv, elements, beanForJsonRpcEndpoints,
                    names.packageName(), names.className() + "$" + method.getSimpleName(), method,
                    Stream.concat(
                                    knownJsonModels.stream(),
                                    jsonModels.keySet().stream())
                            .collect(toSet()),
                    partialOpenRPC,
                    allJsonSchemas.values().stream()
                            .map(GeneratedJsonSchema::content)
                            .filter(it -> it.id() != null)
                            .collect(toMap(JsonSchema::id, identity()))).get();
            writeGeneratedClass(method, generation.endpoint());

            final var bean = generation.bean();
            if (bean != null) {
                writeGeneratedClass(method, bean);
                allBeans.add(bean.name());
            }
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private void generateHttpEndpoint(final ExecutableElement method) {
        if (method.getEnclosingElement() == null || method.getEnclosingElement().getKind() != CLASS) {
            processingEnv.getMessager().printMessage(ERROR, "'" + method + "' is not enclosed by a class: '" + method.getEnclosingElement() + "'");
            return;
        }

        final var names = ParsedName.of(method.getEnclosingElement());
        try {
            final var generation = new HttpEndpointGenerator(
                    processingEnv, elements, beanForHttpEndpoints,
                    names.packageName(), names.className() + "$" + method.getSimpleName(), method,
                    Stream.concat(
                                    knownJsonModels.stream(),
                                    jsonModels.keySet().stream())
                            .collect(toSet())).get();
            writeGeneratedClass(method, generation.endpoint());
            // todo: openapi?

            final var bean = generation.bean();
            if (bean != null) {
                writeGeneratedClass(method, bean);
                allBeans.add(bean.name());
            }
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private void generateCliCommand(final TypeElement runnable) {
        if (!processingEnv.getTypeUtils().isAssignable(
                runnable.asType(),
                processingEnv.getElementUtils().getTypeElement(Runnable.class.getName()).asType())) {
            processingEnv.getMessager().printMessage(ERROR, "'" + runnable + "' is not a Runnable");
            return;
        }

        final var names = ParsedName.of(runnable);
        try {
            final var generation = new CliCommandGenerator(
                    processingEnv, elements, beanForCliCommands,
                    names.packageName(), names.className(),
                    runnable.getAnnotation(Command.class), runnable,
                    allConfigurationsDocs)
                    .get();
            writeGeneratedClass(runnable, generation.command());

            final var bean = generation.bean();
            if (bean != null) {
                writeGeneratedClass(runnable, bean);
                allBeans.add(bean.name());
            }
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private void generateListener(final ExecutableElement listener) {
        final var names = ParsedName.of(listener.getEnclosingElement());
        try {
            final var generation = new ListenerGenerator(
                    processingEnv, elements,
                    names.packageName(), names.className(), "$" + FusionListener.class.getSimpleName() + "$" + listener.getSimpleName(),
                    listener.getSimpleName().toString(), listener.getParameters().get(0),
                    listener.getEnclosingElement())
                    .get();
            writeGeneratedClass(listener, generation);
            allListeners.add(generation.name());
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private void generateImplicitModuleIfNeeded() {
        if ((allBeans.isEmpty() && allListeners.isEmpty()) ||
                !Boolean.parseBoolean(processingEnv.getOptions().getOrDefault("fusion.generateModule", "true"))) {
            return;
        }

        final var moduleName = ofNullable(processingEnv.getOptions().get("fusion.moduleFqn")).orElseGet(this::findModuleName);
        try {
            final var spiLocation = "META-INF/services/" + FusionModule.class.getName();
            final var spi = processingEnv.getFiler().createResource(CLASS_OUTPUT, "", spiLocation);
            try (final var out = spi.openWriter()) {
                out.write(moduleName);
            }
            if (emitNotes) {
                processingEnv.getMessager().printMessage(NOTE, "Generated '" + spiLocation + "'");
            }

            final var names = ParsedName.of(moduleName);
            final var module = new ModuleGenerator(processingEnv, elements, names.packageName(), names.className(),
                    allBeans.stream()
                            .distinct()
                            .sorted()
                            .toList(),
                    allListeners.stream()
                            .distinct()
                            .sorted()
                            .toList())
                    .get();
            writeGeneratedClass(null, module);

            // clean internal state
            allBeans.clear();
            allListeners.clear();
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private void generateProducerBean(final ExecutableElement method) {
        final var enclosingElement = (TypeElement) method.getEnclosingElement();
        final var enclosingName = enclosingElement.getQualifiedName().toString();
        final var names = ParsedName.of(enclosingElement);
        final var suffix = "$" + FusionBean.class.getSimpleName() + "$" + method.getSimpleName().toString();

        try {
            final var bean = new MethodBeanGenerator(processingEnv, elements, enclosingName, names.packageName(), names.className() + suffix, method).get();
            writeGeneratedClass(method, bean);
            allBeans.add(bean.name());
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private void generateBean(final Bean src, final List<FieldInjection> injections) {
        final var names = ParsedName.of(src.enclosing());
        try {
            final String data;
            if (isLazy(src.enclosing()) && src.enclosing() instanceof TypeElement te && src.enclosing().getKind() != RECORD) {
                final var subclass = new SubclassGenerator(processingEnv, elements, names.packageName(), names.className(), te).get();
                writeGeneratedClass(src.enclosing(), subclass);
                data = "" +
                        "\"fusion.framework.subclasses.delegate\",\n" +
                        "(" + Function.class.getName() + "<" + DelegatingContext.class.getName() + "<" +
                        names.className().replace('$', '.') + ">, " + names.className().replace('$', '.') + ">)\n" +
                        "  context -> new " + subclass.name() + "(context)\n";
            } else {
                data = "";
            }

            final var bean = new BeanGenerator(processingEnv, elements, injections, names.packageName(), names.className(), src.enclosing(), data, init, destroy).get();
            writeGeneratedClass(src.enclosing(), bean);
            allBeans.add(bean.name());
        } catch (final IOException | RuntimeException e) {
            processingEnv.getMessager().printMessage(ERROR, e.getMessage());
        }
    }

    private boolean isLazy(final Element element) {
        return elements.findScopeAnnotation(element)
                .map(ann -> ann.getAnnotationType().asElement().getAnnotation(LazyContext.class) != null)
                .orElse(false);
    }

    private TypeElement asTypeElement(final Class<?> type) {
        return (TypeElement) processingEnv.getTypeUtils().asElement(
                processingEnv.getElementUtils().getTypeElement(type.getName()).asType());
    }

    private void writeGeneratedClass(final Element relatedTo, final BaseGenerator.GeneratedClass generated) throws IOException {
        final var out = processingEnv.getFiler().createSourceFile(generated.name(), relatedTo);
        try (final var writer = out.openWriter()) {
            writer.write(generated.content());
        }

        if (emitNotes) {
            processingEnv.getMessager().printMessage(NOTE, "Generated '" + generated.name() + "'");
        }
    }

    private String findModuleName() {
        String current = null;
        // use the shorter package name from all the beans
        for (final var bean : allBeans) {
            var beanPackage = bean;
            int sep = beanPackage.lastIndexOf('.');
            beanPackage = sep > 0 ? beanPackage.substring(0, sep) : "";
            if (current == null) {
                current = beanPackage;
            }
            if (beanPackage.isBlank()) {
                return "FusionGeneratedModule";
            } else if (current.startsWith(beanPackage + '.')) {
                current = beanPackage;
            } else if (beanPackage.startsWith(current + '.') || beanPackage.equals(current)) {
                continue;
            }

            final var segments1 = current.split("\\.");
            final var segments2 = beanPackage.split("\\.");
            final var newCurrent = new StringBuilder();
            for (int i = 0; i < segments1.length; i++) {
                if (segments2.length <= i) {
                    break;
                }
                if (!Objects.equals(segments2[i], segments1[i])) {
                    break;
                }
                newCurrent.append(segments1[i]).append('.');
            }
            if (newCurrent.length() > 0) {
                newCurrent.setLength(newCurrent.length() - 1);
            }
            current = newCurrent.toString();
        }
        return (current != null && !current.isBlank() ? current : "fusion") + '.' + "FusionGeneratedModule";
    }

    private record ParsedName(String packageName, String className) {
        public static ParsedName of(final Element element) {
            if (!(element instanceof TypeElement)) {
                throw new IllegalStateException("Only type elements can be parsed: " + element);
            }

            final var nameList = new ArrayList<Element>();
            var packageElement = element;
            while (packageElement != null && packageElement.getKind() != PACKAGE) {
                nameList.add(packageElement);
                packageElement = packageElement.getEnclosingElement();
            }
            Collections.reverse(nameList);
            if (packageElement != null && packageElement.getKind() == PACKAGE) {
                return new ParsedName(
                        (packageElement instanceof PackageElement pe ? pe.getQualifiedName() : packageElement).toString(),
                        nameList.stream().map(Element::getSimpleName).map(Object::toString).collect(joining("$")));
            }
            return of(element.toString());
        }

        public static ParsedName of(final String fqn) {
            final var lastDot = fqn.lastIndexOf('.');
            final var packageName = lastDot > 0 ? fqn.substring(0, lastDot) : "";
            final var className = packageName.isBlank() ? fqn : fqn.substring(lastDot + 1);
            return new ParsedName(packageName, className);
        }
    }

    private record GeneratedJsonSchema(JsonSchema content) {
    }
}
