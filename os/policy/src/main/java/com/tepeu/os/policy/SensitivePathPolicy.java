package com.tepeu.os.policy;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 参数级 Policy v1 — execution.fs.* 的 path 命中敏感片段则 DENY（叠在名级矩阵上）。
 * 路径归一化：反斜杠→斜杠、小写比较；不替代 WorkspaceJail。
 */
public final class SensitivePathPolicy implements PolicyHook {

    private static final List<String> DENY_FRAGMENTS = List.of(
            ".env",
            ".git/",
            ".ssh/",
            "id_rsa");

    public static SensitivePathPolicy defaults() {
        return new SensitivePathPolicy();
    }

    @Override
    public PolicyVerdict evaluate(TurnContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        String name = syscall.name();
        if (!name.startsWith("execution.fs.")) {
            return PolicyVerdict.ALLOW;
        }
        String path = syscall.args().get("path");
        if (path == null || path.isBlank()) {
            return PolicyVerdict.ALLOW;
        }
        String norm = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        for (String fragment : DENY_FRAGMENTS) {
            if (matches(norm, fragment)) {
                return PolicyVerdict.DENY;
            }
        }
        return PolicyVerdict.ALLOW;
    }

    private static boolean matches(String normPath, String fragment) {
        if (fragment.endsWith("/")) {
            return normPath.contains(fragment);
        }
        if (normPath.equals(fragment)) {
            return true;
        }
        return normPath.endsWith("/" + fragment);
    }
}
