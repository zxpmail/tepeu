package com.tepeu.persist.sqlite;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code Map<String,String>} 的 JSON 对象。只服务 persist_record.fields。 */
final class FieldsJson {

    private FieldsJson() {
    }

    /** 写成 JSON 对象。 */
    static String write(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(quote(e.getKey())).append(':').append(quote(e.getValue() == null ? "" : e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    /** 只认字符串键值。坏 JSON 抛 IllegalStateException。 */
    static Map<String, String> read(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.strip())) {
            return Map.of();
        }
        return Map.copyOf(new Parser(json).object());
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Map<String, String> object() {
            skip();
            expect('{');
            Map<String, String> out = new LinkedHashMap<>();
            skip();
            if (peek() == '}') {
                i++;
                return out;
            }
            while (true) {
                String k = string();
                skip();
                expect(':');
                skip();
                String v = string();
                out.put(k, v);
                skip();
                if (peek() == ',') {
                    i++;
                    skip();
                    continue;
                }
                expect('}');
                return out;
            }
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw new IllegalStateException("fields json");
                        }
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw new IllegalStateException("fields json");
                }
            }
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw new IllegalStateException("fields json");
            }
            return s.charAt(i);
        }

        private char next() {
            char c = peek();
            i++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalStateException("fields json");
            }
        }
    }
}
