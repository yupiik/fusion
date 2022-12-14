package io.yupiik.fusion.json.internal.framework;

import io.yupiik.fusion.framework.api.Instance;
import io.yupiik.fusion.framework.api.RuntimeContainer;
import io.yupiik.fusion.framework.api.configuration.Configuration;
import io.yupiik.fusion.framework.api.container.FusionBean;
import io.yupiik.fusion.framework.api.scope.ApplicationScoped;
import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.JsonMapperImpl;
import io.yupiik.fusion.json.serialization.JsonCodec;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class JsonMapperBean implements FusionBean<JsonMapper> {
    @Override
    public Type type() {
        return JsonMapper.class;
    }

    @Override
    public Class<?> scope() {
        return ApplicationScoped.class;
    }

    @Override
    public JsonMapper create(final RuntimeContainer container, final List<Instance<?>> dependents) {
        final var codecs = container.lookups(JsonCodec.class, i -> i.stream().map(it -> (JsonCodec<?>) it.instance()).toList());
        dependents.add(codecs);
        final var conf = container.lookup(Configuration.class);
        dependents.add(conf);
        return new JsonMapperImpl(new ArrayList<>(codecs.instance()), conf.instance());
    }

    @Override
    public void destroy(final RuntimeContainer container, final JsonMapper instance) {
        instance.close();
    }
}
