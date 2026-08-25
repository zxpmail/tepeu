package com.tepeu.os.identity;

import java.util.Objects;
import java.util.Optional;

/**
 * 命名空间 — 主体动作落在何处。
 * 当前必绑 {@link WorkspaceId}；{@code tenantId} 预留，空 = 无租户维。
 */
public record Namespace(WorkspaceId workspaceId, Optional<String> tenantId) {
    public Namespace {
        Objects.requireNonNull(workspaceId, "workspaceId");
        tenantId = tenantId == null ? Optional.empty() : tenantId;
    }

    /** 单机：仅 workspace，无租户。 */
    public static Namespace ofWorkspace(WorkspaceId workspaceId) {
        return new Namespace(workspaceId, Optional.empty());
    }
}
