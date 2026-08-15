package com.tepeu.os.kernel.identity;

import java.util.Objects;
import java.util.Optional;

/**
 * 命名空间：当前至少绑定 Workspace；租户位预留。
 */
public record Namespace(WorkspaceId workspaceId, Optional<String> tenantId) {
    public Namespace {
        Objects.requireNonNull(workspaceId, "workspaceId");
        tenantId = tenantId == null ? Optional.empty() : tenantId;
    }

    public static Namespace ofWorkspace(WorkspaceId workspaceId) {
        return new Namespace(workspaceId, Optional.empty());
    }
}
