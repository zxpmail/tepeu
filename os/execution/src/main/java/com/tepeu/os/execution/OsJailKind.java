package com.tepeu.os.execution;

import com.tepeu.os.execution.local.BwrapJail;
import com.tepeu.os.execution.local.WindowsJob;

/**
 * OS 级进程 jail 探测。隔离仍报 {@link SandboxIsolation#PARTIAL}（无 landlock / 无受限令牌）。
 * 无 jail 时 spawn 必须失败可见。
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

    public static OsJailKind detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") && WindowsJob.available()) {
            return JOB_OBJECT;
        }
        if (BwrapJail.available()) {
            return BWRAP;
        }
        return NONE;
    }
}
