package com.tepeu.os.identity;

import java.util.Objects;

/**
 * 不透明主体 ID（人 or Agent）。
 */
public record PrincipalId(String value) {
    public PrincipalId {
        Objects.requireNonNull(value, "principalId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("principalId blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
