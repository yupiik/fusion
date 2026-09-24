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
import io.yupiik.fusion.framework.handlebars.compiler.part.ArgEvaluator;
import io.yupiik.fusion.framework.handlebars.compiler.part.BlockHelperPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.ConstantPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.DynamicArgEvaluator;
import io.yupiik.fusion.framework.handlebars.compiler.part.EachVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.part.EmptyPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.EscapedPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.Helpers;
import io.yupiik.fusion.framework.handlebars.compiler.part.IfVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.part.InlineHelperPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.NestedVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.part.Part;
import io.yupiik.fusion.framework.handlebars.compiler.part.PartListPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.SubExpressionArgEvaluator;
import io.yupiik.fusion.framework.handlebars.compiler.part.ThisHelperPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.UnescapedThisPart;
import io.yupiik.fusion.framework.handlebars.compiler.part.UnescapedVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.part.UnlessVariablePart;
import io.yupiik.fusion.framework.handlebars.helper.DefaultHelpers;
import io.yupiik.fusion.framework.handlebars.helper.HelperContext;
import io.yupiik.fusion.framework.handlebars.spi.Accessor;
import io.yupiik.fusion.framework.handlebars.spi.Template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

public class HandlebarsCompiler {
    private final Accessor defaultAccessor;
    private final Map<String, Function<HelperContext, Object>> defaultHelpers = new DefaultHelpers().helpers();
    private final java.util.Set<String> defaultHelperNames = defaultHelpers.keySet();
    private final Map<String, Part> partialsGlobalCache = new ConcurrentHashMap<>();

    public HandlebarsCompiler() {
        this(new MapAccessor());
    }

    public HandlebarsCompiler(final Accessor accessor) {
        this.defaultAccessor = accessor;
    }

    public Template compile(final CompilationContext context) {
        final var partialsCache = context.settings.cachePartials ? partialsGlobalCache : new HashMap<String, Part>();
        final var helpers = new Helpers(mergedHelpers(context.settings));
        return new TemplateImpl(doCompile(context.content(), helpers,
                name -> resolvePartial(name, context.settings, helpers, partialsCache)));
    }

    private Map<String, Function<HelperContext, Object>> mergedHelpers(final Settings settings) {
        if (settings.helpers.isEmpty()) {
            return defaultHelpers;
        }
        final var merged = new HashMap<>(defaultHelpers);
        merged.putAll(settings.helpers);
        return merged;
    }

    private Part resolvePartial(final String name, final Settings settings, final Helpers helpers,
                                final Map<String, Part> cache) {
        return cache.computeIfAbsent(name, k -> ofNullable(settings.partials.get(k))
                .map(tpl -> doCompile(tpl, helpers, n -> resolvePartial(n, settings, helpers, cache)))
                .orElseThrow(() -> new IllegalArgumentException("No partials '" + k + "'")));
    }

    private Part doCompile(final String content, final Helpers helpers, final Function<String, Part> partials) {
        final var parsed = parseBlock(content, 0, null, false, false, helpers, partials);
        return optimizePart(optimize(parsed.thenParts()));
    }

    private Part optimizePart(final List<Part> optimized) {
        return switch (optimized.size()) {
            case 0 -> EmptyPart.INSTANCE;
            case 1 -> optimized.get(0);
            default -> new PartListPart(optimized);
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

    /**
     * Scans {@code content} from {@code from}, collecting parts until the closing {@code {{/closeKeyword}}},
     * an {@code {{else}}}/{@code {{else ...}}} marker or the end of the content.
     */
    private ParsedBlock parseBlock(final String content, final int from, final String closeKeyword, final boolean each,
                                   final boolean stripLeadingEol, final Helpers helpers,
                                   final Function<String, Part> partials) {
        final var parts = new ArrayList<Part>();
        final var buffer = new StringBuilder();
        final var length = content.length();
        var i = from;
        if (stripLeadingEol) {
            while (i < length && content.charAt(i) == '\n') {
                i++;
            }
        }
        while (i < length) {
            final var c = content.charAt(i);
            if (c != '{' || i + 1 >= length || content.charAt(i + 1) != '{') {
                buffer.append(c);
                i++;
                continue;
            }
            final int end = content.indexOf("}}", i + 2);
            if (end < 0) {
                throw new IllegalArgumentException("Unclosed expression at index " + i);
            }
            switch (content.charAt(i + 2)) {
                case '{' -> {
                    if (i + 3 < length && content.charAt(i + 3) == '{') { // raw block {{{{name}}}}...{{{{/name}}}}
                        final var rawName = content.substring(i + 4, end).strip();
                        final var endMarker = "{{{{/" + rawName + "}}}}";
                        final int rawClose = content.indexOf(endMarker, end + 2);
                        if (rawClose < 0) {
                            throw new IllegalArgumentException("Missing " + endMarker + " at index " + i);
                        }
                        flushBuffer(parts, buffer);
                        parts.add(new ConstantPart(content.substring(end + 4, rawClose)));
                        i = rawClose + endMarker.length();
                    } else { // {{{variable}}} / {{{helper args}}}
                        final int end3 = content.indexOf("}}}", i);
                        if (end3 < 0) {
                            throw new IllegalArgumentException("Unclosed expression at index " + i);
                        }
                        flushBuffer(parts, buffer);
                        parts.add(onTripleBrackets(content.substring(i + 3, end3).strip(), helpers));
                        i = end3 + 3;
                    }
                }
                case '#' -> {
                    final var marker = content.substring(i + 3, end).strip();
                    flushBuffer(parts, buffer);
                    final var section = parseSectionOpen(content, end + 2, marker, helpers, partials);
                    parts.add(section.part());
                    i = section.nextIndex();
                }
                case '/' -> {
                    final var close = content.substring(i + 3, end).strip();
                    if (!close.equals(closeKeyword)) {
                        throw new IllegalArgumentException("Mismatched block close '{{/" + close + "}}', expected '{{/" +
                                closeKeyword + "}}' at index " + i);
                    }
                    if (each) {
                        stripTrailingBuffer(buffer);
                    } else {
                        stripEolBuffer(buffer);
                    }
                    flushBuffer(parts, buffer);
                    return new ParsedBlock(parts, EmptyPart.INSTANCE, end + 2);
                }
                case '>' -> {
                    flushBuffer(parts, buffer);
                    parts.add(onPartial(content.substring(i + 3, end), helpers, partials));
                    i = end + 2;
                }
                case '^' -> { // inverted section {{^foo}}...{{/foo}} or {{^}} ({{else}} marker)
                    final var marker = content.substring(i + 3, end).strip();
                    flushBuffer(parts, buffer);
                    if (marker.isEmpty()) { // {{^}} acts like {{else}}
                        if (closeKeyword == null) {
                            throw new IllegalArgumentException("{{^}} outside a block at index " + i);
                        }
                        if (each) {
                            stripTrailingBuffer(buffer);
                        } else {
                            stripEolBuffer(buffer);
                        }
                        flushBuffer(parts, buffer);
                        final var next = onElseMarker(content, end + 2, "else", closeKeyword, each, helpers, partials);
                        i = next.nextIndex();
                        return new ParsedBlock(parts, next.elsePart(), i);
                    }
                    final var inverted = parseInvertedSection(content, end + 2, marker, helpers, partials);
                    parts.add(inverted.part());
                    i = inverted.nextIndex();
                }
                case '!' -> i = onComment(content, i);
                default -> {
                    final var inner = content.substring(i + 2, end).strip();
                    if (isElseMarker(inner)) {
                        if (closeKeyword == null) {
                            throw new IllegalArgumentException("{{else}} outside a block at index " + i);
                        }
                        if (each) {
                            stripTrailingBuffer(buffer);
                        } else {
                            stripEolBuffer(buffer);
                        }
                        flushBuffer(parts, buffer);
                        final var next = onElseMarker(content, end + 2, inner, closeKeyword, each, helpers, partials);
                        i = next.nextIndex();
                        return new ParsedBlock(parts, next.elsePart(), i);
                    }
                    flushBuffer(parts, buffer);
                    parts.add(onDoubleBrackets(inner, helpers));
                    i = end + 2;
                }
            }
        }
        if (closeKeyword != null) {
            throw new IllegalArgumentException("Missing {{/" + closeKeyword + "}} at index " + length);
        }
        flushBuffer(parts, buffer);
        return new ParsedBlock(parts, EmptyPart.INSTANCE, length);
    }

    private boolean isElseMarker(final String stripped) {
        return stripped.equals("else") || stripped.startsWith("else ");
    }

    private ElseResult onElseMarker(final String content, final int fromRest, final String marker, final String closeKeyword,
                                    final boolean each, final Helpers helpers, final Function<String, Part> partials) {
        final var rest = marker.equals("else") ? "" : marker.substring("else ".length()).strip();
        if (rest.isEmpty()) {
            final var b = parseBlock(content, fromRest, closeKeyword, each, true, helpers, partials);
            if (!(b.elsePart() instanceof EmptyPart)) {
                throw new IllegalArgumentException("Duplicate {{else}} at index " + fromRest);
            }
            return new ElseResult(optimizePart(optimize(b.thenParts())), b.nextIndex());
        }
        final int space = rest.indexOf(' ');
        final var keyword = space < 0 ? rest : rest.substring(0, space).strip();
        final var args = space < 0 ? "" : rest.substring(space).strip();
        if (keyword.equals("if") || keyword.equals("unless")) { // {{else if}}: nested conditional sharing the outer close
            final var b = parseBlock(content, fromRest, closeKeyword, each, true, helpers, partials);
            final var cond = singleArg(args, keyword, helpers);
            final var body = optimizePart(optimize(b.thenParts()));
            final var elsePart = b.elsePart();
            final Part chain = keyword.equals("if") ?
                    new IfVariablePart(cond, body, elsePart, defaultAccessor) :
                    new UnlessVariablePart(cond, body, elsePart, defaultAccessor);
            return new ElseResult(chain, b.nextIndex());
        }
        // {{else each/with/<helper> args}}: independent section with its own close, rest is the else branch
        final var section = parseSectionOpen(content, fromRest, rest, helpers, partials);
        final var b = parseBlock(content, section.nextIndex(), closeKeyword, each, true, helpers, partials);
        if (!(b.elsePart() instanceof EmptyPart)) {
            throw new IllegalArgumentException("Duplicate {{else}} at index " + fromRest);
        }
        final var combined = new ArrayList<Part>(b.thenParts().size() + 1);
        combined.add(section.part());
        combined.addAll(b.thenParts());
        return new ElseResult(optimizePart(optimize(combined)), b.nextIndex());
    }

    private Section parseSectionOpen(final String content, final int bodyFrom, final String raw,
                                     final Helpers helpers, final Function<String, Part> partials) {
        final int space = raw.indexOf(' ');
        if (space < 0) { // keyword-less: block helper or if-section on the variable
            if (isBuiltinKeyword(raw)) {
                throw new IllegalArgumentException(raw + " expects exactly 1 argument");
            }
            if (helpers.isHelper(raw)) {
                final var body = parseBlock(content, bodyFrom, raw, false, true, helpers, partials);
                return new Section(new BlockHelperPart(helpers.helper(raw), List.of(), Map.of(),
                        optimizePart(optimize(body.thenParts())), body.elsePart(), defaultAccessor, List.of()), body.nextIndex());
            }
            final var cond = singleArg(raw, "if", helpers);
            final var body = parseBlock(content, bodyFrom, raw, false, true, helpers, partials);
            // like handlebars.js blockHelperMissing: a truthy non-helper value renders the block
            // with the value as the new context, a falsy one renders the else branch
            return new Section(new NestedVariablePart(cond, optimizePart(optimize(body.thenParts())), body.elsePart(),
                    defaultAccessor, List.of()), body.nextIndex());
        }

        // parse the optional block params: {{#each xs as |value key|}} ... {{/each}}
        var argsRaw = raw;
        java.util.List<String> blockParams = List.of();
        final var asIdx = raw.indexOf(" as ");
        if (asIdx > 0) {
            final var afterAs = raw.substring(asIdx + 4).strip();
            if (afterAs.startsWith("|") && afterAs.indexOf('|', 1) > 0) {
                final var pipeEnd = afterAs.indexOf('|', 1);
                blockParams = java.util.Arrays.stream(afterAs.substring(1, pipeEnd).strip().split("\\s+"))
                        .filter(it -> !it.isEmpty())
                        .toList();
                argsRaw = raw.substring(0, asIdx).strip();
            }
        }

        final var keyword = argsRaw.substring(0, space).strip();
        final var parsed = helpers.parseArgs(argsRaw.substring(space).strip());
        return switch (keyword) {
            case "if" -> {
                final var cond = singleArg(parsed, "if");
                noHash(parsed, "if");
                final var body = parseBlock(content, bodyFrom, "if", false, true, helpers, partials);
                yield new Section(new IfVariablePart(cond, optimizePart(optimize(body.thenParts())), body.elsePart(),
                        defaultAccessor), body.nextIndex());
            }
            case "unless" -> {
                final var cond = singleArg(parsed, "unless");
                noHash(parsed, "unless");
                final var body = parseBlock(content, bodyFrom, "unless", false, true, helpers, partials);
                yield new Section(new UnlessVariablePart(cond, optimizePart(optimize(body.thenParts())), body.elsePart(),
                        defaultAccessor), body.nextIndex());
            }
            case "with" -> {
                final var value = singleArg(parsed, "with");
                noHash(parsed, "with");
                final var body = parseBlock(content, bodyFrom, "with", false, true, helpers, partials);
                yield new Section(new NestedVariablePart(value, optimizePart(optimize(body.thenParts())), body.elsePart(),
                        defaultAccessor, blockParams), body.nextIndex());
            }
            case "each" -> {
                final var source = singleArg(parsed, "each");
                final var limit = parsed.hash().get("limit");
                final var offset = parsed.hash().get("offset");
                for (final var key : parsed.hash().keySet()) {
                    if (!key.equals("limit") && !key.equals("offset")) {
                        throw new IllegalArgumentException("Unknown hash argument '" + key + "' for each");
                    }
                }
                final var accessor = source instanceof DynamicArgEvaluator dae ? toAccessor(dae.name()) : defaultAccessor;
                final var itemDefault = accessor instanceof PrecomputedChainAccessor pca ?
                        new ChainedAccessor(accessor, pca.getDelegating()) : accessor;
                final var body = parseBlock(content, bodyFrom, "each", true, true, helpers, partials);
                yield new Section(new EachVariablePart(
                        source, toPartFactory(optimizePart(optimize(body.thenParts()))), accessor, itemDefault,
                        limit, offset, body.elsePart(), blockParams), body.nextIndex());
            }
            default -> {
                final var helper = helpers.helper(keyword);
                if (helper == null) {
                    throw new IllegalArgumentException("Unknown keyword '" + keyword + "'");
                }
                final var body = parseBlock(content, bodyFrom, keyword, false, true, helpers, partials);
                yield new Section(new BlockHelperPart(helper, parsed.args(), parsed.hash(),
                        optimizePart(optimize(body.thenParts())), body.elsePart(), defaultAccessor, blockParams), body.nextIndex());
            }
        };
    }

    private Section parseInvertedSection(final String content, final int bodyFrom, final String raw,
                                         final Helpers helpers, final Function<String, Part> partials) {
        final int space = raw.indexOf(' ');
        final String closeKeyword;
        final String argsRaw;
        if (space < 0) {
            closeKeyword = raw.strip();
            argsRaw = raw.strip();
        } else {
            closeKeyword = raw.substring(0, space).strip();
            argsRaw = raw.substring(space).strip();
        }
        final var cond = singleArg(helpers.parseArgs(argsRaw), "unless");
        final var body = parseBlock(content, bodyFrom, closeKeyword, false, true, helpers, partials);
        // an inverted section renders its block when the value is falsy, the else branch otherwise (= unless)
        return new Section(new UnlessVariablePart(cond, optimizePart(optimize(body.thenParts())), body.elsePart(),
                defaultAccessor), body.nextIndex());
    }

    private boolean isBuiltinKeyword(final String raw) {
        return raw.equals("if") || raw.equals("unless") || raw.equals("each") || raw.equals("with");
    }

    private ArgEvaluator singleArg(final Helpers.ParsedArgs parsed, final String keyword) {
        if (parsed.args().size() != 1) {
            throw new IllegalArgumentException(keyword + " expects exactly 1 argument but got " + parsed.args().size());
        }
        return parsed.args().get(0);
    }

    private ArgEvaluator singleArg(final String raw, final String keyword, final Helpers helpers) {
        return singleArg(helpers.parseArgs(raw), keyword);
    }

    private void noHash(final Helpers.ParsedArgs parsed, final String keyword) {
        if (!parsed.hash().isEmpty()) {
            throw new IllegalArgumentException(keyword + " does not support hash arguments");
        }
    }

    private Part onDoubleBrackets(final String inner, final Helpers helpers) {
        return onMustache(inner, helpers, true);
    }

    private Part onTripleBrackets(final String inner, final Helpers helpers) {
        return onMustache(inner, helpers, false);
    }

    private Part onMustache(final String inner, final Helpers helpers, final boolean escaped) {
        final int space = inner.indexOf(' ');
        if (space > 0) {
            final var name = inner.substring(0, space);
            final var helper = helpers.helper(name);
            if (helper == null) {
                throw new IllegalArgumentException("No helper '" + name + "'");
            }
            final var parsed = helpers.parseArgs(inner.substring(space).strip());
            return new InlineHelperPart(helper, parsed.args(), parsed.hash(), defaultAccessor, escaped);
        }
        final var helper = helpers.helper(inner);
        if (helper == null) {
            return escaped ? new EscapedPart(toVariable(inner)) : toVariable(inner);
        }
        // a bare default helper call has no argument ({{time}} renders "now") while a user helper
        // receives the current data as its single argument (like {{print_person}})
        return defaultHelperNames.contains(inner) ?
                new InlineHelperPart(helper, List.of(), Map.of(), defaultAccessor, escaped) :
                new ThisHelperPart(helper, escaped);
    }

    private Part onPartial(final String inner, final Helpers helpers, final Function<String, Part> partials) {
        final var stripped = inner.strip();
        final int space = stripped.indexOf(' ');
        final String name;
        final String params;
        if (space < 0) {
            name = stripped;
            params = "";
        } else {
            name = stripped.substring(0, space).strip();
            params = stripped.substring(space).strip();
        }
        final var base = partials.apply(name);
        if (params.isEmpty()) {
            return base;
        }
        final var parsed = helpers.parseArgs(params);
        if (!parsed.args().isEmpty()) {
            throw new IllegalArgumentException(
                    "Partial parameters must be key=value but got positional arguments in '" + params + "'");
        }
        return remapParts(base, parsed.hash());
    }

    private int onComment(final String content, final int i) {
        if (content.length() > i + 5 && content.startsWith("{{!--", i)) {
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

    private int toNextInterestingChar(final String content, int currentEnd) {
        int end = currentEnd;
        while (content.length() > end && content.charAt(end) == '\n') {
            end++;
        }
        return end;
    }

    private Part toVariable(final String name) {
        if ("this".equals(name) || ".".equals(name)) {
            return new UnescapedThisPart();
        }
        // `../` (parent path) and `@../` (parent data variable) must not be split on the dot:
        // the DynamicArgEvaluator resolves them against the render context frames
        if (name.startsWith("../") || name.startsWith("@../")) {
            return new UnescapedVariablePart(new DynamicArgEvaluator(name), defaultAccessor);
        }
        final int split = name.indexOf('.');
        if (split < 0) {
            return new UnescapedVariablePart(new DynamicArgEvaluator(name), defaultAccessor);
        }
        return new NestedVariablePart(new DynamicArgEvaluator(name.substring(0, split)),
                toVariable(name.substring(split + 1)), EmptyPart.INSTANCE, defaultAccessor, List.of());
    }

    private Accessor toAccessor(final String value) {
        final int sep = value.indexOf('.');
        if (sep > 0 && sep != value.length() - 1) {
            final var first = value.substring(0, sep);
            return new PrecomputedChainAccessor(first, value, defaultAccessor, toAccessor(value.substring(sep + 1)));
        }
        return defaultAccessor;
    }

    private void flushBuffer(final List<Part> out, final StringBuilder buffer) {
        if (buffer.length() > 0) {
            out.add(new ConstantPart(buffer.toString()));
            buffer.setLength(0);
        }
    }

    private void stripEolBuffer(final StringBuilder buffer) {
        while (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) == '\n') {
            buffer.setLength(buffer.length() - 1);
        }
    }

    private void stripTrailingBuffer(final StringBuilder buffer) {
        if (buffer.length() > 0) {
            final var stripped = buffer.toString().stripTrailing();
            buffer.setLength(0);
            buffer.append(stripped);
        }
    }

    // todo: optimize since there are several cases we know which are just constant (most of them)
    private Function<Accessor, Part> toPartFactory(final Part part) {
        if (part instanceof EmptyPart ||
                part instanceof ConstantPart ||
                part instanceof ThisHelperPart ||
                part instanceof UnescapedThisPart) {
            return a -> part;
        }

        if (part instanceof EachVariablePart p) {
            final var accessor = p.accessor();
            final var dynamicElse = toPartFactory(p.elsePart());
            if (!(accessor instanceof PrecomputedChainAccessor ca)) {
                return a -> new EachVariablePart(p.source(), p.itemPartFactory(), a, a,
                        p.limit(), p.offset(), dynamicElse.apply(a), p.blockParams());
            }
            return acc -> {
                final var nameAccessor = new ByNameAccessor(Map.of(ca.getSupportedName(), ca), acc);
                return new EachVariablePart(p.source(), p.itemPartFactory(), nameAccessor, nameAccessor,
                        p.limit(), p.offset(), dynamicElse.apply(acc), p.blockParams());
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
            final var dynamicElse = toPartFactory(p.elsePart());
            return acc -> new BlockHelperPart(p.helper(), p.args(), p.hash(), dynamicSub.apply(acc),
                    dynamicElse.apply(acc), acc, p.blockParams());
        }
        if (part instanceof IfVariablePart p) {
            final var dynamicNext = toPartFactory(p.next());
            final var dynamicElse = toPartFactory(p.elsePart());
            return acc -> new IfVariablePart(p.condition(), dynamicNext.apply(acc), dynamicElse.apply(acc), acc);
        }
        if (part instanceof NestedVariablePart p) {
            final var dynamicNext = toPartFactory(p.next());
            final var dynamicElse = toPartFactory(p.elsePart());
            return acc -> new NestedVariablePart(p.value(), dynamicNext.apply(acc), dynamicElse.apply(acc), acc, p.blockParams());
        }
        if (part instanceof UnlessVariablePart p) {
            final var dynamicNext = toPartFactory(p.next());
            final var dynamicElse = toPartFactory(p.elsePart());
            return acc -> new UnlessVariablePart(p.condition(), dynamicNext.apply(acc), dynamicElse.apply(acc), acc);
        }
        if (part instanceof InlineHelperPart p) {
            return acc -> new InlineHelperPart(p.helper(), p.args(), p.hash(), acc, p.escaped());
        }
        if (part instanceof UnescapedVariablePart p) {
            return acc -> new UnescapedVariablePart(p.name(), acc);
        }
        throw new IllegalArgumentException("Unknown part type, update code please: " + part);
    }

    private Part remapParts(final Part part, final Map<String, ArgEvaluator> params) {
        if (part instanceof EmptyPart ||
                part instanceof ConstantPart ||
                part instanceof ThisHelperPart ||
                part instanceof UnescapedThisPart) {
            return part;
        }
        if (part instanceof PartListPart p) {
            return optimizePart(optimize(p.delegates().stream().map(it -> remapParts(it, params)).toList()));
        }
        if (part instanceof EscapedPart p) {
            return new EscapedPart(remapParts(p.delegate(), params));
        }
        if (part instanceof BlockHelperPart p) {
            return new BlockHelperPart(p.helper(), remapEvs(p.args(), params), remapHash(p.hash(), params),
                    remapParts(p.subPart(), params), remapParts(p.elsePart(), params), p.accessor(), p.blockParams());
        }
        if (part instanceof IfVariablePart p) {
            return new IfVariablePart(remapEval(p.condition(), params), remapParts(p.next(), params),
                    remapParts(p.elsePart(), params), p.accessor());
        }
        if (part instanceof NestedVariablePart p) {
            return new NestedVariablePart(remapEval(p.value(), params), remapParts(p.next(), params),
                    remapParts(p.elsePart(), params), p.accessor(), p.blockParams());
        }
        if (part instanceof UnlessVariablePart p) {
            return new UnlessVariablePart(remapEval(p.condition(), params), remapParts(p.next(), params),
                    remapParts(p.elsePart(), params), p.accessor());
        }
        if (part instanceof InlineHelperPart p) {
            return new InlineHelperPart(p.helper(), remapEvs(p.args(), params), remapHash(p.hash(), params), p.accessor(), p.escaped());
        }
        if (part instanceof UnescapedVariablePart p) {
            return new UnescapedVariablePart(remapEval(p.name(), params), p.accessor());
        }
        if (part instanceof EachVariablePart p) {
            final var newSource = remapEval(p.source(), params);
            final var newAccessor = newSource instanceof DynamicArgEvaluator dae ? toAccessor(dae.name()) : p.accessor();
            final var itemDefault = newAccessor instanceof PrecomputedChainAccessor pca ?
                    new ChainedAccessor(newAccessor, pca.getDelegating()) : newAccessor;
            return new EachVariablePart(newSource, acc -> remapParts(p.itemPartFactory().apply(acc), params),
                    newAccessor, itemDefault, remapEval(p.limit(), params), remapEval(p.offset(), params),
                    remapParts(p.elsePart(), params), p.blockParams());
        }
        throw new IllegalArgumentException("Unknown part type, update code please: " + part);
    }

    private List<ArgEvaluator> remapEvs(final List<ArgEvaluator> evs, final Map<String, ArgEvaluator> params) {
        return evs.stream().map(it -> remapEval(it, params)).toList();
    }

    private Map<String, ArgEvaluator> remapHash(final Map<String, ArgEvaluator> hash, final Map<String, ArgEvaluator> params) {
        if (hash.isEmpty()) {
            return hash;
        }
        final var out = new HashMap<String, ArgEvaluator>(hash.size());
        for (final var entry : hash.entrySet()) {
            out.put(entry.getKey(), remapEval(entry.getValue(), params));
        }
        return out;
    }

    private ArgEvaluator remapEval(final ArgEvaluator eval, final Map<String, ArgEvaluator> params) {
        if (eval instanceof DynamicArgEvaluator dae && params.containsKey(dae.name())) {
            return params.get(dae.name());
        }
        if (eval instanceof SubExpressionArgEvaluator se) {
            return new SubExpressionArgEvaluator(se.helper(), remapEvs(se.args(), params), remapHash(se.hash(), params));
        }
        return eval;
    }

    private record Section(Part part, int nextIndex) {
    }

    private record ParsedBlock(List<Part> thenParts, Part elsePart, int nextIndex) {
    }

    private record ElseResult(Part elsePart, int nextIndex) {
    }

    private record TemplateImpl(Part part) implements Template {
    }

    public static class Settings {
        private static final Settings DEFAULTS = new Settings();

        private boolean cachePartials = false;
        private Map<String, Function<HelperContext, Object>> helpers = Map.of();
        private Map<String, String> partials = Map.of();

        public Settings cachePartials(final boolean cachePartials) {
            this.cachePartials = cachePartials;
            return this;
        }

        public Settings helpers(final Map<String, Function<HelperContext, Object>> helpers) {
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