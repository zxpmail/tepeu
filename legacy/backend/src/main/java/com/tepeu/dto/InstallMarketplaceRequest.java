package com.tepeu.dto;

/**
 * 从市场安装技能。
 * 关联：MarketplaceController。
 */
public class InstallMarketplaceRequest {

    private String workspaceId;
    /** 目录条目 id（优先） */
    private String entryId;
    /** 或按 slug 安装 */
    private String slug;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}
