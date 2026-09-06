package com.tepeu.os.identity;

import java.util.Objects;

/**
 * 不透明主体 ID — branded string；人与 Agent 共用，种类见 {@link Principal#kind()}。
 * 非空、非空白；相等与哈希按 value。
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
