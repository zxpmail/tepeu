package com.tepeu.os.identity;

import java.util.Objects;

/**
 * 工作区 ID — 命名空间的主载体（单机阶段）。
 */
public record WorkspaceId(String value) {
    public WorkspaceId {
        Objects.requireNonNull(value, "workspaceId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("workspaceId blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
