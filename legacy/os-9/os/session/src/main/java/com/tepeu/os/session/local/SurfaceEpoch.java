package com.tepeu.os.session.local;

import com.tepeu.os.session.*;

/**
 * surface 世代键 — 存在 {@link SessionRegisters}（{@link #KEY}），不是第四个 store。
 * {@code replaceRange} 后 bump；上笔 llm digest 与当前 surface 不对齐则跳过复核（防 ASSERTION）。
 */
public final class SurfaceEpoch {

    public static final String KEY = "log.surfaceEpoch";

    private SurfaceEpoch() {
    }

    public static String current(SessionRegisters registers) {
        return registers.get(KEY).orElse("0");
    }

    public static String bump(SessionRegisters registers) {
        String next = next(current(registers));
        registers.put(KEY, next);
        return next;
    }

    public static String next(String current) {
        int n = 0;
        if (current != null && !current.isBlank()) {
            n = Integer.parseInt(current.trim());
        }
        return Integer.toString(n + 1);
    }
}
