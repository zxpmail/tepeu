package com.tepeu.os.session;

/**
 * surface 世代 — {@code replaceRange} 后模型读面改写，上笔 llm digest 不再对当前 surface 成立。
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
