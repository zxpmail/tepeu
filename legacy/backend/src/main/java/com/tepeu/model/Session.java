package com.tepeu.model;

import java.time.LocalDateTime;

public class Session {
    private String id;
    private String workspaceId;
    private String title;
    /** 分支来源会话 ID；主会话为 null。 */
    private String parentSessionId;
    /** 从哪条消息处分叉；主会话为 null。 */
    private String forkFromMessageId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Session() {}

    public Session(String id, String workspaceId, String title) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.title = title;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getParentSessionId() { return parentSessionId; }
    public void setParentSessionId(String parentSessionId) { this.parentSessionId = parentSessionId; }
    public String getForkFromMessageId() { return forkFromMessageId; }
    public void setForkFromMessageId(String forkFromMessageId) { this.forkFromMessageId = forkFromMessageId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
