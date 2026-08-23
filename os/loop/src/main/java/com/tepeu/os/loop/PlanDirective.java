package com.tepeu.os.loop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loop 步进语言：模型输出首行 {@code plan ...} 则落 {@code PLAN_STEP}。
 * 与 {@link ToolDirective} 并列，不是第三份 LLM 投影。
 */
public record PlanDirective(String body, Map<String, String> attrs) {

    public PlanDirective {
        Objects.requireNonNull(body, "body");
        if (body.isBlank()) {
            throw new IllegalArgumentException("plan body blank");
        }
        attrs = attrs == null ? Map.of() : Map.copyOf(attrs);
    }

    public static Optional<PlanDirective> parse(String output) {
        if (output == null) {
            return Optional.empty();
        }
        String text = output.strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        String[] lines = text.split("\\R", -1);
        String head = lines[0].strip();
        if (!"plan".equals(head) && !head.startsWith("plan ")) {
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
        String rest = head.equals("plan") ? "" : head.substring("plan ".length()).strip();
        String body = rest.isEmpty() ? parsed.getOrDefault("text", "(plan)") : rest;
        if (body.isBlank()) {
            body = "(plan)";
        }
        return Optional.of(new PlanDirective(body, parsed));
    }
}
