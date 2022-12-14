package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.FusionListener;
import io.yupiik.fusion.framework.api.container.FusionModule;
import io.yupiik.fusion.framework.processor.Elements;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.Collection;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class ModuleGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String className;
    private final Collection<String> allBeans;
    private final Collection<String> allListeners;

    public ModuleGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                           final String packageName, final String className,
                           final Collection<String> allBeans, final Collection<String> allListeners) {
        super(processingEnv, elements);
        this.packageName = packageName;
        this.className = className;
        this.allBeans = allBeans;
        this.allListeners = allListeners;
    }

    @Override
    public GeneratedClass get() {
        final var out = new StringBuilder();

        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n");
            out.append("\n");
        }
        out.append("import ").append(Stream.class.getName()).append(";\n");
        out.append("import ").append(FusionBean.class.getName()).append(";\n");
        out.append("import ").append(FusionListener.class.getName()).append(";\n");
        out.append("import ").append(FusionModule.class.getName()).append(";\n");
        out.append("\n");
        out.append("public class ").append(className).append(" implements ").append(FusionModule.class.getSimpleName()).append(" {\n");
        out.append("    @Override\n");
        out.append("    public Stream<FusionBean<?>> beans() {\n");
        out.append("        return Stream.of(\n");
        out.append(allBeans.stream()
                .sorted()
                .map(it -> "            new " + it + "()")
                .collect(joining(",\n", "", "\n")));
        out.append("        );\n");
        out.append("    }\n");
        out.append("\n");
        out.append("    @Override\n");
        out.append("    public Stream<FusionListener<?>> listeners() {\n");
        out.append("        return Stream.of(\n");
        out.append(allListeners.stream()
                .sorted()
                .map(it -> "            new " + it + "()")
                .collect(joining(",\n", "", "\n")));
        out.append("        );\n");
        out.append("    }\n");
        out.append("}\n\n");

        return new GeneratedClass((!packageName.isBlank() ? packageName + '.' : "") + className, out.toString());
    }
}
