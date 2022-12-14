package io.yupiik.fusion.json.pretty;

import io.yupiik.fusion.json.JsonMapper;
import io.yupiik.fusion.json.internal.formatter.SimplePrettyFormatter;
import io.yupiik.fusion.json.mapper.DelegatingMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class PrettyJsonMapper extends DelegatingMapper {
    private final Charset charset;
    private final Function<String, String> prettifier;

    public PrettyJsonMapper(final JsonMapper mapper) {
        this(mapper, StandardCharsets.UTF_8);
    }

    public PrettyJsonMapper(final JsonMapper mapper, final Charset charset) {
        super(mapper);
        this.charset = charset;
        this.prettifier = new SimplePrettyFormatter(mapper);
    }

    private String prettify(final String json) {
        return prettifier.apply(json);
    }

    @Override
    public <A> byte[] toBytes(final A instance) {
        return prettify(new String(super.toBytes(instance), charset)).getBytes(charset);
    }

    @Override
    public <A> String toString(final A instance) {
        return prettify(super.toString(instance));
    }

    @Override
    public <A> void write(final A instance, final Writer out) {
        final var writer = new StringWriter();
        try (writer) {
            super.write(instance, writer);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        try {
            out.write(prettify(writer.toString()));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
