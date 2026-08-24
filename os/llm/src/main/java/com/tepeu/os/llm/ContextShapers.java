package com.tepeu.os.llm;

/**
 * 内置 ContextShaper 工厂。
 */
public final class ContextShapers {

    public static final String DEFAULT_VERSION = RedactingContextShaper.VERSION;

    private ContextShapers() {
    }

    public static ContextShaper none() {
        return turns -> turns;
    }

    /** 发行默认：密钥 redact + TOOL_RESULT 超长截断。 */
    public static ContextShaper defaults() {
        return new RedactingContextShaper(RedactingContextShaper.DEFAULT_MAX_TOOL_BODY);
    }

    public static ContextShaper redacting(int maxToolBodyChars) {
        return new RedactingContextShaper(maxToolBodyChars);
    }
}
