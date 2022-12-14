package io.yupiik.fusion.json.internal;

public final class JsonStrings {
    private JsonStrings() {
        // no-op
    }

    public static String escape(final String value) {
        final var sb = new StringBuilder(value.length());
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i); // todo: check toCharArray is not faster for small chars and switch the impl?
            if (isPassthrough(c)) {
                sb.append(c);
                continue;
            }
            sb.append(escape(c));
        }
        sb.append('"');
        return sb.toString();
    }

    public static String escape(final char c) {
        if (isPassthrough(c)) {
            return String.valueOf(c);
        }
        return switch (c) {
            case '"', '\\' -> "\\" + c;
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> {
                final var hex = "000" + Integer.toHexString(c);
                yield "\\u" + hex.substring(hex.length() - 4);
            }
        };
    }

    public static char asEscapedChar(final char current) {
        return switch (current) {
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case '"' -> '\"';
            case '\\' -> '\\';
            case '/' -> '/';
            case '[' -> '[';
            case ']' -> ']';
            default -> {
                if (Character.isHighSurrogate(current) || Character.isLowSurrogate(current)) {
                    yield current;
                }
                throw new IllegalStateException("Invalid escape sequence '" + current + "' (Codepoint: " + String.valueOf(current).codePointAt(0));
            }
        };
    }

    private static boolean isPassthrough(final char c) {
        return c >= 0x20 && c != 0x22 && c != 0x5c;
    }
}
