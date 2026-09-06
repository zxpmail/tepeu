package com.tepeu.identity;

import java.util.Objects;

/** 哪个工作区。非空、非空白。不是磁盘路径。 */
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
