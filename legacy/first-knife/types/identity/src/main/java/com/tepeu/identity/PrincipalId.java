package com.tepeu.identity;

import java.util.Objects;

/** 谁。非空、非空白。 */
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
