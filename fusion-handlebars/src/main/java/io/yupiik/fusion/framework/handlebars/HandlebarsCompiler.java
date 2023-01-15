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
package io.yupiik.fusion.framework.handlebars;

import io.yupiik.fusion.framework.handlebars.compiler.BlockHelperPart;
import io.yupiik.fusion.framework.handlebars.compiler.ConstantPart;
import io.yupiik.fusion.framework.handlebars.compiler.EachVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.EscapedPart;
import io.yupiik.fusion.framework.handlebars.compiler.IfVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.InlineHelperPart;
import io.yupiik.fusion.framework.handlebars.compiler.NestedVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.Part;
import io.yupiik.fusion.framework.handlebars.compiler.RemappedDataPart;
import io.yupiik.fusion.framework.handlebars.compiler.ThisHelper;
import io.yupiik.fusion.framework.handlebars.compiler.UnescapedThisPart;
import io.yupiik.fusion.framework.handlebars.compiler.UnescapedVariablePart;
import io.yupiik.fusion.framework.handlebars.compiler.UnlessVariablePart;
import io.yupiik.fusion.framework.handlebars.spi.Template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

// todo: better cache for partials? enables to pre-register them on the compiler?
public class HandlebarsCompiler {
    public Template compile(final CompilationContext context) {
        final var partialsCache = new HashMap<String, Part>();
        final var part = doCompile(
                context.content(),
                context.helpers(),
                name -> partialsCache.computeIfAbsent(
                        name,
                        k -> ofNullable(context.partials().get(name))
                                .map(tpl -> compile(new CompilationContext(
                                        tpl, context.helpers(), context.partials())))
                                .orElseThrow(() -> new IllegalArgumentException("No partials '" + name + "'"))));
        return new TemplateImpl(part);
    }

    private Part doCompile(final String content,
                           final Map<String, Function<Object, String>> helpers,
                           final Function<String, Part> partials) {
        final var optimized = optimize(parse(content, helpers, partials));
        return switch (optimized.size()) {
            case 0 -> (ctx, data) -> "";
            case 1 -> optimized.get(0);
            default -> (ctx, data) -> optimized.stream().map(p -> p.apply(ctx, data)).collect(joining());
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
                    content.substring(space + 1, end).strip()));
        } else {
            final var name = content.substring(i + "{{".length(), end);
            out.add(ofNullable(helpers.get(name))
                    .<Part>map(ThisHelper::new)
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
        final Map<Object, Object> mapping = Stream.of(value.split(" "))
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
                .map(Map.Entry::getKey);
        final Function<Map<?, ?>, Map<?, ?>> remapping = thisName
                .map(thisNameValue -> {
                    final var remapped = new HashMap<>(mapping);
                    remapped.remove(thisNameValue);
                    if (remapped.isEmpty()) {
                        return (Function<Map<?, ?>, Map<?, ?>>) map -> Map.of(thisNameValue, map);
                    }
                    return (Function<Map<?, ?>, Map<?, ?>>) map -> {
                        final Map<Object, Object> outMap = map.entrySet().stream()
                                .collect(toMap(e -> remapped.getOrDefault(e.getKey(), e.getKey()), Map.Entry::getValue));
                        outMap.put(thisNameValue, map);
                        return outMap;
                    };
                })
                .orElseGet(() -> map -> map.entrySet().stream()
                        .collect(toMap(e -> mapping.getOrDefault(e.getKey(), e.getKey()), Map.Entry::getValue)));

        out.add(new RemappedDataPart(remapping, partial));

        return end + "}}".length();
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
        if (space < 0) {
            throw new IllegalArgumentException("Missing space in brackets at index " + i);
        }
        final var keyword = string.substring(0, space);
        final var value = string.substring(space).strip();
        final int nextIndex = end + "}}".length();
        return switch (keyword) {
            case "with" -> {
                final int endBlock = content.indexOf("{{/with}}", nextIndex);
                if (endBlock < 0) {
                    throw new IllegalArgumentException("Missing {{/with}} at index " + nextIndex + "'");
                }
                final var substring = stripSurroundingEol(content.substring(nextIndex, endBlock));
                out.add(new NestedVariablePart(value, doCompile(substring, helpers, partials)));
                yield endBlock + "{{/with}}".length();
            }
            case "each" -> {
                final int endBlock = content.indexOf("{{/each}}", nextIndex);
                if (endBlock < 0) {
                    throw new IllegalArgumentException("Missing {{/each}} at index " + nextIndex + "'");
                }
                final var itemPart = doCompile(stripSurroundingEol(content.substring(nextIndex, endBlock)), helpers, partials);
                out.add(new EachVariablePart(value, itemPart));
                yield endBlock + "{{/each}}".length();
            }
            case "if" -> {
                final int endBlock = content.indexOf("{{/if}}", nextIndex);
                if (endBlock < 0) {
                    throw new IllegalArgumentException("Missing {{/if}} at index " + nextIndex + "'");
                }
                final var itemPart = doCompile(stripSurroundingEol(content.substring(nextIndex, endBlock)), helpers, partials);
                out.add(new IfVariablePart(value, itemPart));
                yield endBlock + "{{/if}}".length();
            }
            case "unless" -> {
                final int endBlock = content.indexOf("{{/unless}}", nextIndex);
                if (endBlock < 0) {
                    throw new IllegalArgumentException("Missing {{/unless}} at index " + nextIndex + "'");
                }
                final var itemPart = doCompile(stripSurroundingEol(content.substring(nextIndex, endBlock)), helpers, partials);
                out.add(new UnlessVariablePart(value, itemPart));
                yield endBlock + "{{/unless}}".length();
            }
            default -> ofNullable(helpers.get(keyword))
                    .map(helper -> {
                        final int endBlock = content.indexOf("{{/" + keyword + "}}", nextIndex);
                        if (endBlock < 0) {
                            throw new IllegalArgumentException("Missing {{/each}} at index " + nextIndex + "'");
                        }
                        final var itemPart = doCompile(stripSurroundingEol(content.substring(nextIndex, endBlock)), helpers, partials);
                        out.add(new BlockHelperPart(helper, value, itemPart));
                        return endBlock + "{{/}}".length() + keyword.length();
                    })
                    .orElseThrow(() -> new IllegalArgumentException("Unknown keyword '" + keyword + "'"));
        };
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
                    yield new UnescapedVariablePart(name);
                }
                yield new NestedVariablePart(name.substring(0, split), toVariable(name.substring(split + 1)));
            }
        };
    }

    private List<Part> optimize(final List<Part> parts) {
        final var constants = new ArrayList<ConstantPart>();
        final var out = new ArrayList<Part>(parts.size());
        for (final var current : parts) {
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

    private record TemplateImpl(BiFunction<RenderContext, Object, String> renderer) implements Template {
        @Override
        public String apply(final RenderContext context, final Object data) {
            return renderer.apply(context, data);
        }
    }

    public record CompilationContext(String content,
                                     Map<String, Function<Object, String>> helpers,
                                     Map<String, String> partials) {
    }
}
