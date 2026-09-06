package com.tepeu.os.execution;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 随 execution.* 携带的隔离策略。路径囚笼 + 可选 OS jail；完备性仍是
 * {@link SandboxIsolation#PARTIAL}（无 landlock / 无受限令牌，禁止报 FULL）。
 * jail 由调用方传入（本机默认 {@code OsJails.detect()}）。
 */
public record SandboxPolicy(Path workspaceRoot, OsJailKind jail) {

    public SandboxPolicy {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(jail, "jail");
    }

    public SandboxIsolation isolation() {
        return SandboxIsolation.PARTIAL;
    }

    public String mechanism() {
        return jail.mechanism();
    }
}
