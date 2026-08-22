package com.tepeu.os.llm;

/** 双协议族（第十轮）。 */
public enum ProtocolFamily {
    ANTHROPIC,
    OPENAI;

    public static ProtocolFamily parse(String raw) {
        if (raw == null || raw.isBlank() || "anthropic".equalsIgnoreCase(raw)) {
            return ANTHROPIC;
        }
        if ("openai".equalsIgnoreCase(raw)) {
            return OPENAI;
        }
        throw new IllegalArgumentException("unknown protocol family: " + raw);
    }
}
