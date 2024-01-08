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
package io.yupiik.fusion.framework.handlebars;

import io.yupiik.fusion.framework.handlebars.compiler.accessor.ByNameAccessor;
import io.yupiik.fusion.framework.handlebars.compiler.accessor.ChainedAccessor;
import io.yupiik.fusion.framework.handlebars.compiler.accessor.MapAccessor;
import io.yupiik.fusion.framework.handlebars.compiler.accessor.PrecomputedChainAccessor;
import io.yupiik.fusion.framework.handlebars.compiler.part.BlockHelperPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.ConstantPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.EachVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.part.EmptyPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.EscapedPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.IfVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.part.InlineHelperPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.NestedVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.part.Part;
import io.yupiik.fusion.framework.handlebars.compiler.part.PartListPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.ThisHelperPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.UnescapedThisPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.UnescapedVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.part.UnlessVariablePart;
import io.yupiik.fusion.framework.handlebars.spi.Accessor;
import io.yupiik.fusion.framework.handlebars.spi.Template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

// todo: better cache for partials? enables to pre-register them on the compiler?
public class HandlebarsCompiler {
    private final Accessor defaultAccessor;
    private final Map<String, Part> partialsGlobalCache = new ConcurrentHashMap<>();

    public HandlebarsCompiler() {
        this(new MapAccessor());
    }

    public HandlebarsCompiler(final Accessor accessor) {
        this.defaultAccessor = accessor;
    }

    public Template compile(final CompilationContext context) {
        final var partialsCache = context.settings.cachePartials ? partialsGlobalCache : new HashMap<String, Part>();
        final var part = doCompile(
                context.content(),
                context.settings.helpers,
                name -> partialsCache.computeIfAbsent(
                        name,
                        k -> ofNullable(context.settings.partials.get(name))
                                .map(tpl -> compile(new CompilationContext(context.settings, tpl)))
                                .orElseThrow(() -> new IllegalArgumentException("No partials '" + name + "'"))
                                .part()));
        return new TemplateImpl(part);
    }

    private Part doCompile(final String content,
                           final Map<String, Function<Object, String>> helpers,
                           final Function<String, Part> partials) {
        return optimizePart(optimize(parse(content, helpers, partials)));
    }

    private Part optimizePart(final List<Part> optimized) {
        return switch (optimized.size()) {
            case 0 -> EmptyPart.INSTANCE;
            case 1 -> optimized.get(0);
            default -> new PartListPart(optimized);
        };
    }

    private List<Part> parse(final String content,
                             final Map<String, Function<Object, String>> helpers,
                             final Function<String, Part> partials) {
        final var out = new ArrayList<Part>();
        final var chars = content.toCharArray();

        final var buffer = new StringBuilder();
        for (int i = 0; i < chars.length; ) {
            final var c = chars[i];
            switch (c) {
                case '{' -> i = onOpeningBrace(content, out, chars, buffer, i, c, helpers, partials);
                default -> {
                    buffer.append(c);
                    i++;
                }
            }
        }

        flushBuffer(out, buffer);

        return out;
    }

    private int onOpeningBrace(final String content, final List<Part> out,
                               final char[] chars, final StringBuilder buffer,
                               final int i, final char c,
                               final Map<String, Function<Object, String>> helpers,
                               final Function<String, Part> partials) {
        if (i < chars.length - 1) {
            final var next1 = chars[i + 1];
            if (next1 == '{') {
                if (i < chars.length - 2) {
                    final var next2 = chars[i + 2];
                    return switch (next2) {
                        case '{' -> onTripleBrackets(content, out, buffer, i);
                        case '#' -> onDoubleBracketsAndHash(content, out, buffer, i, helpers, partials);
                        case '!' -> onComment(content, i);
                        case '>' -> onPartials(content, out, buffer, i, partials);
                        default -> onDoubleBrackets(content, out, buffer, i, helpers);
                    };
                }
            }
        }
        buffer.append(c);
        return i + 1;
    }

    private int onDoubleBrackets(final String content, final List<Part> out, final StringBuilder buffer, final int i,
                                 final Map<String, Function<Object, String>> helpers) {
        final int end = content.indexOf("}}", i);
        if (end < 0) {
            throw new IllegalArgumentException("Unclosed expression at index " + i);
        }

        flushBuffer(out, buffer);

        final int space = content.indexOf(' ', i);
        if (space < end && space > 0) { // helper
            final var helperName = content.substring(i + "{{".length(), space);
            out.add(new InlineHelperPart(
                    requireNonNull(helpers.get(helperName), () -> "No helper '" + helperName + "'"),
                    content.substring(space + 1, end).strip(),
                    defaultAccessor));
        } else {
            final var name = content.substring(i + "{{".length(), end);
            out.add(ofNullable(helpers.get(name))
                    .<Part>map(ThisHelperPart::new)
                    .orElseGet(() -> new EscapedPart(toVariable(name))));
        }
        return end + "}}".length();
    }

    private int onTripleBrackets(final String content, final List<Part> out, final StringBuilder buffer, final int i) {
        final int end = content.indexOf("}}}", i);
        if (end < 0) {
            throw new IllegalArgumentException("Unclosed expression at index " + i);
        }

        flushBuffer(out, buffer);
        out.add(toVariable(content.substring(i + "{{{".length(), end)));
        return end + "}}}".length();
    }

    private int onPartials(final String content, final List<Part> out, final StringBuilder buffer, final int i, final Function<String, Part> partials) {
        final int end = content.indexOf("}}", i);
        if (end < 0) {
            throw new IllegalArgumentException("Unclosed expression at index " + i);
        }

        flushBuffer(out, buffer);

        final var string = content.substring(i + "{{>".length(), end);
        final int space = string.indexOf(' ');
        if (space < 0) {
            final var name = string.strip();
            final var partial = partials.apply(name);
            if (partial == null) {
                throw new IllegalArgumentException("Missing partial '" + name + "'");
            }
            out.add(partial);
            return end + "}}".length();
        }

        final var name = string.substring(0, space);
        final var partial = partials.apply(name);
        if (partial == null) {
            throw new IllegalArgumentException("Missing partial '" + name + "'");
        }

        final var value = string.substring(space).strip();
        final var mapping = Stream.of(value.split(" "))
                .map(it -> {
                    final int equal = it.indexOf('=');
                    return new String[]{
                            it.substring(0, equal).strip(),
                            it.substring(equal + 1).strip()
                    };
                })
                .collect(toMap(it -> it[0], it -> it[1]));
        final var thisName = mapping.entrySet().stream()
                .filter(e -> ".".equals(e.getValue()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
        if (thisName != null) {
            mapping.remove(thisName);
        }

        out.add(remapParts(partial, mapping, thisName));

        return end + "}}".length();
    }

    // this works because Part is a sealed interface
    private Part remapParts(final Part partial,
                            final Map<String, String> remapped,
                            final String thisName) {
        // for now we just remap "next" level and ignore nested ones using another level (each will use N+1 for ex)
        if (partial instanceof EmptyPart ||
                partial instanceof ConstantPart ||
                partial instanceof ThisHelperPart ||
                partial instanceof UnescapedThisPart ||
                partial instanceof EachVariablePart) {
            return partial;
        }

        if (partial instanceof PartListPart p) {
            return optimizePart(optimize(p.delegates().stream().map(it -> remapParts(it, remapped, thisName)).toList()));
        }
        if (partial instanceof EscapedPart p) {
            return new EscapedPart(remapParts(p.delegate(), remapped, thisName));
        }
        if (partial instanceof BlockHelperPart p) {
            return findRemappedName(p.name(), remapped, thisName)
                    .<Part>map(name -> new BlockHelperPart(p.helper(), name, p.subPart(), p.accessor()))
                    .orElse(EmptyPart.INSTANCE);
        }
        if (partial instanceof IfVariablePart p) {
            return findRemappedName(p.name(), remapped, thisName)
                    .<Part>map(name -> new IfVariablePart(name, p.next(), p.accessor()))
                    .orElse(EmptyPart.INSTANCE);
        }
        if (partial instanceof InlineHelperPart p) {
            return findRemappedName(p.name(), remapped, thisName)
                    .<Part>map(name -> new InlineHelperPart(p.helper(), name, p.accessor()))
                    .orElse(EmptyPart.INSTANCE);
        }
        if (partial instanceof NestedVariablePart p) {
            return findRemappedName(p.name(), remapped, thisName)
                    .<Part>map(name -> new NestedVariablePart(name, p.next(), p.accessor()))
                    .orElse(EmptyPart.INSTANCE);

        }
        if (partial instanceof UnescapedVariablePart p) {
            return findRemappedName(p.name(), remapped, thisName)
                    .<Part>map(name -> new UnescapedVariablePart(name, p.accessor()))
                    .orElse(EmptyPart.INSTANCE);
        }
        if (partial instanceof UnlessVariablePart p) {
            return findRemappedName(p.name(), remapped, thisName)
                    .<Part>map(name -> new UnlessVariablePart(name, p.next(), p.accessor()))
                    .orElse(EmptyPart.INSTANCE);
        }
        throw new IllegalArgumentException("Unknown part type, update code please: " + partial);
    }

    // todo: optimize since there are several cases we know which are just constant (most of them)
    private Function<Accessor, Part> toPartFactory(final Part part) {
        // for now we just remap "next" level and ignore nested ones using another level (each will use N+1 for ex)
        if (part instanceof EmptyPart ||
                part instanceof ConstantPart ||
                part instanceof ThisHelperPart ||
                part instanceof UnescapedThisPart) {
            return a -> part;
        }

        if (part instanceof EachVariablePart p) {
            final var accessor = p.accessor();
            if (!(accessor instanceof PrecomputedChainAccessor ca)) {
                return a -> new EachVariablePart(p.name(), p.itemPartFactory(), a, a);
            }
            return acc -> {
                final var nameAccessor = new ByNameAccessor(Map.of(ca.getSupportedName(), ca), acc);
                return new EachVariablePart(p.name(), p.itemPartFactory(), nameAccessor, nameAccessor);
            };
        }
        if (part instanceof PartListPart p) {
            if (p.delegates().stream().allMatch(it -> it instanceof EmptyPart ||
                    it instanceof ConstantPart ||
                    it instanceof ThisHelperPart ||
                    it instanceof UnescapedThisPart)) {
                return acc -> p; // no need of any recomputation
            }
            final var delegates = p.delegates().stream().map(this::toPartFactory).toList();
            return acc -> {
                final var newDelegates = delegates.stream().map(it -> it.apply(acc)).toList();
                return new PartListPart(newDelegates);
            };
        }
        if (part instanceof EscapedPart p) {
            if (p.delegate() instanceof EmptyPart ||
                    p.delegate() instanceof ConstantPart ||
                    p.delegate() instanceof ThisHelperPart ||
                    p.delegate() instanceof UnescapedThisPart) {
                return acc -> p;
            }
            final var delegate = toPartFactory(p.delegate());
            return acc -> new EscapedPart(delegate.apply(acc));
        }
        if (part instanceof BlockHelperPart p) {
            final var dynamicSub = toPartFactory(p.subPart());
            return acc -> new BlockHelperPart(p.helper(), p.name(), dynamicSub.apply(acc), acc);
        }
        if (part instanceof IfVariablePart p) {
            final var dynamicNext = toPartFactory(p.next());
            return acc -> new IfVariablePart(p.name(), dynamicNext.apply(acc), acc);
        }
        if (part instanceof InlineHelperPart p) {
            return acc -> new InlineHelperPart(p.helper(), p.name(), acc);
        }
        if (part instanceof NestedVariablePart p) {
            final var dynamicNext = toPartFactory(p.next());
            return acc -> new NestedVariablePart(p.name(), dynamicNext.apply(acc), acc);
        }
        if (part instanceof UnescapedVariablePart p) {
            return acc -> new UnescapedVariablePart(p.name(), acc);
        }
        if (part instanceof UnlessVariablePart p) {
            final var dynamicNext = toPartFactory(p.next());
            return acc -> new UnlessVariablePart(p.name(), dynamicNext.apply(acc), acc);
        }
        throw new IllegalArgumentException("Unknown part type, update code please: " + part);
    }

    private Optional<String> findRemappedName(final String name, final Map<String, String> remapped, final String thisName) {
        if (thisName != null && thisName.equals(name)) {
            return of(".");
        }
        return ofNullable(remapped.get(name));
    }

    private int onComment(final String content, final int i) {
        if (content.length() > i + 5 && content.substring(i, i + 5).startsWith("{{!--")) {
            final int end = content.indexOf("--}}", i);
            if (end < 0) {
                throw new IllegalArgumentException("Unclosed expression at index " + i);
            }
            return toNextInterestingChar(content, end + "--}}".length());
        }

        final int end = content.indexOf("}}", i);
        if (end < 0) {
            throw new IllegalArgumentException("Unclosed expression at index " + i);
        }
        return toNextInterestingChar(content, end + "}}".length());
    }

    private int onDoubleBracketsAndHash(final String content, final List<Part> out, final StringBuilder buffer, final int i,
                                        final Map<String, Function<Object, String>> helpers,
                                        final Function<String, Part> partials) {
        final int end = content.indexOf("}}", i);
        if (end < 0) {
            throw new IllegalArgumentException("Unclosed expression at index " + i);
        }

        flushBuffer(out, buffer);

        final var string = content.substring(i + "{{#".length(), end);
        final int space = string.indexOf(' ');
        final String keyword;
        final String value;
        if (space < 0) {
            keyword = "";
            value = string;
        } else {
            keyword = string.substring(0, space);
            value = string.substring(space).strip();
        }
        final int nextIndex = end + "}}".length();
        return switch (keyword) {
            case "" -> {
                final var endBlockMarker = "{{/" + value + "}}";
                final int endBlock = findEndBlock("{{#" + value, endBlockMarker, content, nextIndex);
                if (endBlock < 0) {
                    throw new IllegalArgumentException("Missing " + endBlockMarker + " at index " + nextIndex + "'");
                }
                final var itemPart = doCompile(stripSurroundingEol(content.substring(nextIndex, endBlock)), helpers, partials);
                out.add(new IfVariablePart(value, itemPart, toAccessor(value)));
                yield endBlock + "{{/}}".length() + value.length();
            }
            case "with" -> {
                final int endBlock = findEndBlock("{{#with", "{{/with}}", content, nextIndex);
                if (endBlock < 0) {
                    throw new IllegalArgumentException("Missing {{/with}} at index " + nextIndex + "'");
                }
                final var substring = stripSurroundingEol(content.substring(nextIndex, endBlock));
                out.add(new NestedVariablePart(value, doCompile(substring, helpers, partials), toAccessor(value)));
                yield endBlock + "{{/with}}".length();
            }
            case "each" -> {
                final int endBlock = findEndBlock("{{#each", "{{/each}}", content, nextIndex);
                if (endBlock < 0) {
                    throw new IllegalArgumentException("Missing {{/each}} at index " + nextIndex + "'");
                }
                final var eachContent = stripSurroundingEol(content.substring(nextIndex, endBlock)).stripTrailing();
                final var itemPart = doCompile(eachContent, helpers, partials);
                final var accessor = toAccessor(value);
                out.add(new EachVariablePart(
                        value, toPartFactory(itemPart), accessor,
                        accessor instanceof PrecomputedChainAccessor pca ?
                                new ChainedAccessor(accessor, pca.getDelegating()) : accessor));
                yield endBlock + "{{/each}}".length();
            }
            case "if" -> {
                final int endBlock = findEndBlock("{{#if", "{{/if}}", content, nextIndex);
                if (endBlock < 0) {
                    throw new IllegalArgumentException("Missing {{/if}} at index " + nextIndex + "'");
                }
                final var itemPart = doCompile(stripSurroundingEol(content.substring(nextIndex, endBlock)), helpers, partials);
                out.add(new IfVariablePart(value, itemPart, toAccessor(value)));
                yield endBlock + "{{/if}}".length();
            }
            case "unless" -> {
                final int endBlock = findEndBlock("{{#unless", "{{/unless}}", content, nextIndex);
                if (endBlock < 0) {
                    throw new IllegalArgumentException("Missing {{/unless}} at index " + nextIndex + "'");
                }
                final var itemPart = doCompile(stripSurroundingEol(content.substring(nextIndex, endBlock)), helpers, partials);
                out.add(new UnlessVariablePart(value, itemPart, toAccessor(value)));
                yield endBlock + "{{/unless}}".length();
            }
            default -> ofNullable(helpers.get(keyword))
                    .map(helper -> {
                        final int endBlock = findEndBlock("{{#" + keyword, "{{/" + keyword + "}}", content, nextIndex);
                        if (endBlock < 0) {
                            throw new IllegalArgumentException("Missing {{/" + keyword + "}} at index " + nextIndex + "'");
                        }
                        final var itemPart = doCompile(stripSurroundingEol(content.substring(nextIndex, endBlock)), helpers, partials);
                        out.add(new BlockHelperPart(helper, value, itemPart, toAccessor(value)));
                        return endBlock + "{{/}}".length() + keyword.length();
                    })
                    .orElseThrow(() -> new IllegalArgumentException("Unknown keyword '" + keyword + "'"));
        };
    }

    private Accessor toAccessor(final String value) {
        final int sep = value.indexOf('.');
        if (sep > 0 && sep != value.length() - 1) {
            final var first = value.substring(0, sep);
            return new PrecomputedChainAccessor(first, value, defaultAccessor, toAccessor(value.substring(sep + 1)));
        }
        return defaultAccessor;
    }

    private int findEndBlock(final String start, final String end, final String content, final int nextIndex) {
        final int endBlock = content.indexOf(end, nextIndex);
        if (endBlock > 0) {
            final int endBlock2 = content.indexOf(start, nextIndex);
            if (endBlock2 > 0 && endBlock2 < endBlock) { // has a nested opening tag
                return findEndBlock(start, end, content, endBlock + end.length());
            }
        }
        return endBlock;
    }

    private int toNextInterestingChar(final String content, int currentEnd) {
        int end = currentEnd;
        while (content.length() > end && content.charAt(end) == '\n') {
            end++;
        }
        return end;
    }

    // can be optimized but shouldn't be a ton of chars
    private String stripSurroundingEol(final String in) {
        if (in.startsWith("\n")) {
            return stripSurroundingEol(in.substring(1));
        }
        if (in.endsWith("\n")) {
            return stripSurroundingEol(in.substring(0, in.length() - 1));
        }
        return in;
    }

    private void flushBuffer(final List<Part> out, final StringBuilder buffer) {
        if (buffer.length() > 0) {
            out.add(new ConstantPart(buffer.toString()));
            buffer.setLength(0);
        }
    }

    private Part toVariable(final String name) {
        return switch (name) {
            case "this" -> new UnescapedThisPart();
            default -> {
                final int split = name.indexOf('.');
                if (split < 0) {
                    yield new UnescapedVariablePart(name, defaultAccessor);
                }
                yield new NestedVariablePart(name.substring(0, split), toVariable(name.substring(split + 1)), defaultAccessor);
            }
        };
    }

    private List<Part> optimize(final List<Part> parts) {
        final var constants = new ArrayList<ConstantPart>();
        final var out = new ArrayList<Part>(parts.size());
        for (final var current : parts) {
            if (current instanceof EmptyPart) {
                continue;
            }
            if (current instanceof ConstantPart cp) {
                constants.add(cp);
                continue;
            }

            flush(constants, out);
            out.add(current);
        }

        flush(constants, out);
        return out;
    }

    private void flush(final List<ConstantPart> constants, final ArrayList<Part> out) {
        if (!constants.isEmpty()) {
            out.add(switch (constants.size()) {
                case 1 -> constants.get(0);
                default -> new ConstantPart(constants.stream().map(ConstantPart::value).collect(joining()));
            });
            constants.clear();
        }
    }

    private record TemplateImpl(Part part) implements Template {
    }

    public static class Settings {
        private static final Settings DEFAULTS = new Settings();

        private boolean cachePartials = false;
        private Map<String, Function<Object, String>> helpers = Map.of();
        private Map<String, String> partials = Map.of();

        public Settings cachePartials(final boolean cachePartials) {
            this.cachePartials = cachePartials;
            return this;
        }

        public Settings helpers(final Map<String, Function<Object, String>> helpers) {
            this.helpers = helpers;
            return this;
        }

        public Settings partials(final Map<String, String> partials) {
            this.partials = partials;
            return this;
        }
    }

    public record CompilationContext(Settings settings, String content) {
        public CompilationContext(final String content) {
            this(Settings.DEFAULTS, content);
        }
    }
}
