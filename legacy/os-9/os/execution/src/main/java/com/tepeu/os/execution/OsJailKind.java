package com.tepeu.os.execution;

/**
 * OS 级进程 jail 种类。探测在 {@code execution.local}；无 jail 时 spawn 必须失败可见。
 * 隔离仍报 {@link SandboxIsolation#PARTIAL}（无 landlock / 无受限令牌）。
 */
public enum OsJailKind {
    JOB_OBJECT("job-object"),
    BWRAP("bwrap"),
    NONE("workspace-root");

    private final String mechanism;

    OsJailKind(String mechanism) {
        this.mechanism = mechanism;
    }

    public String mechanism() {
        return mechanism;
    }

    public boolean canSpawn() {
        return this != NONE;
    }
}
