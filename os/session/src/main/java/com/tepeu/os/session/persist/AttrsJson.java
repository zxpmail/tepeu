package com.tepeu.os.session.persist;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 扁平 {@code Map<String,String>} ↔ JSON 对象 — 仅供会话 attrs 列。
 * 刻意手写：不得依赖 llm 的 CanonicalJson；不引入 Jackson/Gson。
 */
final class AttrsJson {

    private AttrsJson() {
    }

    static String write(Map<String, String> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return "{}";
        }
        TreeMap<String, String> ordered = new TreeMap<>(attrs);
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            quote(sb, e.getKey());
            sb.append(':');
            quote(sb, e.getValue() == null ? "" : e.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    static Map<String, String> read(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.strip())) {
            return Map.of();
        }
        String s = json.strip();
        if (s.charAt(0) != '{' || s.charAt(s.length() - 1) != '}') {
            throw new IllegalArgumentException("attrs json: " + json);
        }
        s = s.substring(1, s.length() - 1).strip();
        if (s.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        int i = 0;
        while (i < s.length()) {
            while (i < s.length() && (s.charAt(i) == ',' || Character.isWhitespace(s.charAt(i)))) {
                i++;
            }
            if (i >= s.length()) {
                break;
            }
            Parse p = parseString(s, i);
            String key = p.value;
            i = p.next;
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            if (i >= s.length() || s.charAt(i) != ':') {
                throw new IllegalArgumentException("attrs json colon: " + json);
            }
            i++;
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            p = parseString(s, i);
            out.put(key, p.value);
            i = p.next;
        }
        return Map.copyOf(out);
    }

    private static Parse parseString(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i >= s.length() || s.charAt(i) != '"') {
            throw new IllegalArgumentException("expected string at " + i);
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                return new Parse(sb.toString(), i);
            }
            if (c == '\\' && i < s.length()) {
                char n = s.charAt(i++);
                sb.append(switch (n) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> n;
                });
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("unterminated string");
    }

    private static void quote(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
    }

    private record Parse(String value, int next) {
    }
}
