package com.tepeu.os.loop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Loop 步进语言：模型输出首行 {@code syscall <name>} 则视为一次工具请求。
 * 不是第三份 LLM 投影；真 HTTP 落码后再把 tool_use 块映射到这里。
 */
public record ToolDirective(String name, Map<String, String> args) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9._-]*");

    public ToolDirective {
        Objects.requireNonNull(name, "name");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("syscall name: " + name);
        }
        args = args == null ? Map.of() : Map.copyOf(args);
    }

    public static Optional<ToolDirective> parse(String output) {
        if (output == null) {
            return Optional.empty();
        }
        String text = output.strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        String[] lines = text.split("\\R", -1);
        String head = lines[0].strip();
        if (!head.startsWith("syscall ")) {
            return Optional.empty();
        }
        String name = head.substring("syscall ".length()).strip();
        if (name.isEmpty() || name.indexOf(' ') >= 0 || !NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            parsed.put(line.substring(0, eq).strip(), line.substring(eq + 1));
        }
        return Optional.of(new ToolDirective(name, parsed));
    }
}
