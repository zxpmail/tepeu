package com.tepeu.os.execution.local;

import com.tepeu.os.execution.OsJailKind;

/** 本机 jail 探测。根包枚举不探环境。 */
public final class OsJails {

    private OsJails() {
    }

    public static OsJailKind detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") && WindowsJob.available()) {
            return OsJailKind.JOB_OBJECT;
        }
        if (BwrapJail.available()) {
            return OsJailKind.BWRAP;
        }
        return OsJailKind.NONE;
    }
}
