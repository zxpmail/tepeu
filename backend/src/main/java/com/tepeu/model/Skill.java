package com.tepeu.model;

import java.time.LocalDateTime;

/**
 * 工作区技能 — Markdown 正文；对话中用 /slug 或 @slug 调用后注入 SystemMessage。
 * enabled 表示「常用」置顶，不自动注入每一轮。
 * 关联：SkillRepository、SkillService、AgentOrchestrator。
 */
public class Skill {
    private String id;
    private String workspaceId;
    private String slug;
    private String name;
    private String description;
    private String content;
    private boolean enabled;
    private boolean builtin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Skill() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isBuiltin() { return builtin; }
    public void setBuiltin(boolean builtin) { this.builtin = builtin; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
