package com.tepeu.os.kernel.conformance;

/**
 * 断言助手 — 只抛 AssertionError，不依赖任何测试框架。
 */
public final class ConformanceCheck {

    private ConformanceCheck() {
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void checkEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static <T extends Throwable> T expectThrows(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError(
                    "expected " + type.getSimpleName() + " but got "
                            + t.getClass().getSimpleName() + ": " + t,
                    t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }
}
