package com.tepeu.os.identity;

import java.util.Objects;

/**
 * 工作区 ID — 当前命名空间的主载体（单机）；租户见 {@link Namespace#tenantId()}。
 * 非空、非空白。不是磁盘路径（路径由 execution 囚笼绑定）。
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
