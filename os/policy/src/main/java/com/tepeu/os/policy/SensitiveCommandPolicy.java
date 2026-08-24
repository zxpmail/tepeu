package com.tepeu.os.policy;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;

import java.util.List;
import java.util.Objects;

/**
 * 参数级 Policy v1 — execution.proc.spawn 归一化后命中危险片段 / shell 元字符则 DENY。
 */
public final class SensitiveCommandPolicy implements PolicyHook {

    private static final List<String> DEFAULT_DENY_FRAGMENTS = List.of(
            "powershell -enc",
            "-encodedcommand",
            "rm -rf",
            "del /f",
            "format c:",
            "curl ",
            "wget ");

    private static final List<String> METACHAR = List.of("|", "&&", "||", ";", ">", "<", "`", "$(");

    private final List<String> denyFragments;

    public SensitiveCommandPolicy(List<String> denyFragments) {
        this.denyFragments = List.copyOf(denyFragments == null ? DEFAULT_DENY_FRAGMENTS : denyFragments);
    }

    public static SensitiveCommandPolicy defaults() {
        return new SensitiveCommandPolicy(DEFAULT_DENY_FRAGMENTS);
    }

    @Override
    public PolicyVerdict evaluate(TurnContext ctx, Syscall syscall) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(syscall, "syscall");
        if (!"execution.proc.spawn".equals(syscall.name())) {
            return PolicyVerdict.ALLOW;
        }
        String path = syscall.args().get("path");
        String args = syscall.args().get("args");
        if (containsMetachar(path) || containsMetachar(args)) {
            return PolicyVerdict.DENY;
        }
        String normalized = CommandLineNormalizer.normalize(syscall);
        if (normalized.isEmpty()) {
            return PolicyVerdict.ALLOW;
        }
        for (String fragment : denyFragments) {
            if (normalized.contains(fragment)) {
                return PolicyVerdict.DENY;
            }
        }
        return PolicyVerdict.ALLOW;
    }

    private static boolean containsMetachar(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        for (String token : METACHAR) {
            if (raw.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
