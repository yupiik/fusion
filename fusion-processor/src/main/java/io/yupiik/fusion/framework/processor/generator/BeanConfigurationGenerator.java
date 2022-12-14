package io.yupiik.fusion.framework.processor.generator;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.container.bean.BaseBean;
import io.yupiik.fusion.framework.api.scope.DefaultScoped;
import io.yupiik.fusion.framework.processor.Elements;

import javax.annotation.processing.ProcessingEnvironment;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class BeanConfigurationGenerator extends BaseGenerator implements Supplier<BaseGenerator.GeneratedClass> {
    private final String packageName;
    private final String className;

    public BeanConfigurationGenerator(final ProcessingEnvironment processingEnv, final Elements elements,
                                      final String packageName, final String className) {
        super(processingEnv, elements);
        this.packageName = packageName;
        this.className = className;
    }

    @Override
    public GeneratedClass get() {
        final var pckPrefix = packageName.isBlank() ? "" : (packageName + '.');
        final var simpleName = className + "$RootConfiguration$" + FusionBean.class.getSimpleName();
        final var confBeanClassName = pckPrefix + simpleName;

        final var out = new StringBuilder();
        if (!packageName.isBlank()) {
            out.append("package ").append(packageName).append(";\n\n");
        }
        out.append("public class ").append(simpleName).append(" extends ")
                .append(BaseBean.class.getName()).append("<").append(pckPrefix).append(className).append("> {\n");
        out.append("  public ").append(simpleName).append("() {\n");
        out.append("    super(")
                .append(pckPrefix).append(className).append(".class, ")
                .append(DefaultScoped.class.getName()).append(".class, ")
                .append("1000, ")
                .append(Map.class.getName()).append(".of());\n");
        out.append("  }\n");
        out.append("\n");
        out.append("  @Override\n");
        out.append("  public ").append(className).append(" create(final ").append(RuntimeContainer.class.getName())
                .append(" container, final ")
                .append(List.class.getName()).append("<").append(Instance.class.getName()).append("<?>> dependents) {\n");
        out.append("    final var conf = lookup(container, ").append(Configuration.class.getName()).append(".class, dependents);\n");
        out.append("    return new ").append(pckPrefix).append(className).append(ConfigurationFactoryGenerator.SUFFIX).append("(conf).get();\n");
        out.append("  }\n");
        out.append("}\n\n");

        return new GeneratedClass(confBeanClassName, out.toString());
    }
}
