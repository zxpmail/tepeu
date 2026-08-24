package com.tepeu.os.policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code policy.rules} 统一解析 — syscall 名级 override + {@code deny-path} / {@code deny-command} 参数级扩展。
 */
public final class PolicyRulesFile {

    private static final List<String> DEFAULT_DENY_PATHS = List.of(".env", ".git/", ".ssh/", "id_rsa");
    private static final List<String> DEFAULT_DENY_COMMANDS = List.of(
            "powershell -enc",
            "-encodedcommand",
            "rm -rf",
            "del /f",
            "format c:",
            "curl ",
            "wget ");

    private final DefaultRuleMatrix matrix;
    private final List<String> denyPaths;
    private final List<String> denyCommands;

    public PolicyRulesFile(DefaultRuleMatrix matrix, List<String> denyPaths, List<String> denyCommands) {
        this.matrix = matrix == null ? DefaultRuleMatrix.defaults() : matrix;
        this.denyPaths = List.copyOf(denyPaths == null ? List.of() : denyPaths);
        this.denyCommands = List.copyOf(denyCommands == null ? List.of() : denyCommands);
    }

    public static PolicyRulesFile builtins() {
        return new PolicyRulesFile(DefaultRuleMatrix.defaults(), DEFAULT_DENY_PATHS, DEFAULT_DENY_COMMANDS);
    }

    public static PolicyRulesFile empty() {
        return new PolicyRulesFile(DefaultRuleMatrix.defaults(), List.of(), List.of());
    }

    public static PolicyRulesFile parse(String text) {
        Map<String, PolicyVerdict> overrides = new LinkedHashMap<>();
        List<String> paths = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return builtins();
        }
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("deny-path ")) {
                paths.add(line.substring("deny-path ".length()).strip().toLowerCase(Locale.ROOT));
                continue;
            }
            if (line.startsWith("deny-command ")) {
                commands.add(line.substring("deny-command ".length()).strip().toLowerCase(Locale.ROOT));
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length != 2) {
                throw new IllegalArgumentException("policy.rules line " + (i + 1) + ": " + line);
            }
            PolicyVerdict verdict;
            try {
                verdict = PolicyVerdict.valueOf(parts[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("policy.rules line " + (i + 1) + " verdict: " + parts[1]);
            }
            overrides.put(parts[0], verdict);
        }
        DefaultRuleMatrix matrix = overrides.isEmpty()
                ? DefaultRuleMatrix.defaults()
                : new DefaultRuleMatrix(overrides);
        List<String> mergedPaths = merge(DEFAULT_DENY_PATHS, paths);
        List<String> mergedCommands = merge(DEFAULT_DENY_COMMANDS, commands);
        return new PolicyRulesFile(matrix, mergedPaths, mergedCommands);
    }

    public DefaultRuleMatrix matrix() {
        return matrix;
    }

    public List<String> denyPaths() {
        return denyPaths;
    }

    public List<String> denyCommands() {
        return denyCommands;
    }

    public PolicyHook composePolicy() {
        return PolicyHooks.compose(
                matrix,
                new SensitivePathPolicy(denyPaths),
                new SensitiveCommandPolicy(denyCommands));
    }

    private static List<String> merge(List<String> base, List<String> extra) {
        if (extra.isEmpty()) {
            return base;
        }
        List<String> out = new ArrayList<>(base);
        for (String item : extra) {
            if (!item.isBlank() && !out.contains(item)) {
                out.add(item);
            }
        }
        return List.copyOf(out);
    }
}
