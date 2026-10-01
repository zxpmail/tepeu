package com.tepeu.identity;

import java.util.Objects;

/** 哪一次对话。非空、非空白、不含 {@code '/'}（存储空间按键拼接，会话名是单段）。 */
public record SessionId(String value) {
    public SessionId {
        Objects.requireNonNull(value, "sessionId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("sessionId blank");
        }
        if (value.indexOf('/') >= 0) {
            throw new IllegalArgumentException("sessionId must not contain '/': " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
