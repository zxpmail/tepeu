package com.tepeu.policy.local;

import com.tepeu.identity.InvokeContext;
import com.tepeu.policy.PolicyHook;
import com.tepeu.policy.PolicyVerdict;
import com.tepeu.syscall.Syscall;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 第一刀默认矩阵：问模型与读放行；写文件、创建进程需批准；未登记一律 DENY。
 * 精确名 override 优先。
 */
public final class DefaultRuleMatrix implements PolicyHook {

    private final Map<String, PolicyVerdict> overrides;

    public DefaultRuleMatrix() {
        this(Map.of());
    }

    public DefaultRuleMatrix(Map<String, PolicyVerdict> overrides) {
        this.overrides = Map.copyOf(overrides == null ? Map.of() : overrides);
    }

    /** 行格式 {@code 名称 裁决}。空行与 {@code #} 注释忽略。 */
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

    @Override
    public PolicyVerdict evaluate(InvokeContext ctx, Syscall syscall) {
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
        if ("execution.fs.read".equals(name) || "execution.sandbox.probe".equals(name)) {
            return PolicyVerdict.ALLOW;
        }
        if ("execution.fs.write".equals(name) || name.startsWith("execution.proc.")) {
            return PolicyVerdict.NEED_APPROVAL;
        }
        return PolicyVerdict.DENY;
    }
}
