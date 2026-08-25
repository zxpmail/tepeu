package com.tepeu.os.identity;

import java.util.Objects;

/**
 * 会话标识 — 「哪一次会话」的词，不是 SessionStore / 三 store 本身。
 * 非空、非空白。
 */
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
