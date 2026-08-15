package com.tepeu.os.kernel.session;

import java.util.Objects;

/** 会话 ID。 */
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
