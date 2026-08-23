package com.tepeu.os.execution;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 随 execution.* 携带的隔离策略。路径囚笼 + 可选 OS jail；完备性仍是
 * {@link SandboxIsolation#PARTIAL}（无 landlock / 无受限令牌，禁止报 FULL）。
 */
public record SandboxPolicy(Path workspaceRoot, OsJailKind jail) {

    public SandboxPolicy {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        jail = jail == null ? OsJailKind.detect() : jail;
    }

    public SandboxPolicy(Path workspaceRoot) {
        this(workspaceRoot, OsJailKind.detect());
    }

    public SandboxIsolation isolation() {
        return SandboxIsolation.PARTIAL;
    }

    public String mechanism() {
        return jail.mechanism();
    }
}
