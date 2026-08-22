package com.tepeu.os.identity;

import java.util.Objects;

/** 会话标识 — 身份词汇（哪一次会话），不是会话本身。 */
public record SessionId(String value) {
    public SessionId {
        Objects.requireNonNull(value, "sessionId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("sessionId blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
