package com.tepeu.os.policy.local;

import com.tepeu.os.policy.*;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 默认规则矩阵 — 层5 做真。未知 DENY。llm.* 放行；写盘/进程 ASK；探活放行。
 * 精确名 override 优先。不是 ALLOW-all；裸总线未装配仍 C2 fail-closed。
 */
public final class DefaultRuleMatrix implements PolicyHook {

    private final Map<String, PolicyVerdict> overrides;

    public DefaultRuleMatrix() {
        this(Map.of());
    }

    public DefaultRuleMatrix(Map<String, PolicyVerdict> overrides) {
        this.overrides = Map.copyOf(overrides == null ? Map.of() : overrides);
    }

    public static DefaultRuleMatrix defaults() {
        return new DefaultRuleMatrix();
    }

    /**
     * 行格式：{@code name ALLOW|DENY|NEED_APPROVAL}。空行与 {@code #} 注释忽略。
     */
    public static DefaultRuleMatrix parse(String text) {
        Map<String, PolicyVerdict> parsed = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return new DefaultRuleMatrix(parsed);
        }
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
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
            parsed.put(parts[0], verdict);
        }
        return new DefaultRuleMatrix(parsed);
    }

    public DefaultRuleMatrix withOverride(String syscallName, PolicyVerdict verdict) {
        Objects.requireNonNull(syscallName, "syscallName");
        Objects.requireNonNull(verdict, "verdict");
        Map<String, PolicyVerdict> next = new LinkedHashMap<>(overrides);
        next.put(syscallName, verdict);
        return new DefaultRuleMatrix(next);
    }

    @Override
    public PolicyVerdict evaluate(TurnContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        String name = syscall.name();
        PolicyVerdict override = overrides.get(name);
        if (override != null) {
            return override;
        }
        if (name.startsWith("llm.")) {
            return PolicyVerdict.ALLOW;
        }
        if ("execution.sandbox.probe".equals(name) || "execution.fs.read".equals(name)) {
            return PolicyVerdict.ALLOW;
        }
        if ("execution.fs.write".equals(name) || name.startsWith("execution.proc.")) {
            return PolicyVerdict.NEED_APPROVAL;
        }
        return PolicyVerdict.DENY;
    }
}
