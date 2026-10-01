package com.tepeu.loop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 最小工具请求协议（2026-09-14 裁）：输出首行 {@code @tool 名称 k=v;k=v} 即工具请求，
 * 其余为最终答复；首行是工具请求时后续行忽略。
 * 名称不含空白；值不含 {@code ;} 与换行。解析只认格式：不合式按最终答复处理。
 */
public record ToolRequest(String name, Map<String, String> args, String line) {

    private static final String PREFIX = "@tool ";

    public ToolRequest {
        Objects.requireNonNull(name, "name");
        args = args == null ? Map.of() : Map.copyOf(args);
        Objects.requireNonNull(line, "line");
    }

    public static Optional<ToolRequest> parse(String output) {
        if (output == null) {
            return Optional.empty();
        }
        int nl = output.indexOf('\n');
        String first = nl < 0 ? output : output.substring(0, nl);
        if (!first.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = first.substring(PREFIX.length()).trim();
        if (rest.isEmpty()) {
            return Optional.empty();
        }
        int space = rest.indexOf(' ');
        String name = space < 0 ? rest : rest.substring(0, space);
        if (name.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> args = new LinkedHashMap<>();
        if (space >= 0) {
            for (String pair : rest.substring(space + 1).split(";")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    return Optional.empty();
                }
                String key = pair.substring(0, eq).trim();
                if (key.isBlank()) {
                    return Optional.empty();
                }
                args.put(key, pair.substring(eq + 1));
            }
        }
        return Optional.of(new ToolRequest(name, args, first));
    }
}
