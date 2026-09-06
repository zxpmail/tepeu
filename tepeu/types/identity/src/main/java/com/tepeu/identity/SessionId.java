package com.tepeu.identity;

import java.util.Objects;

/** 哪一次对话。非空、非空白。 */
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
