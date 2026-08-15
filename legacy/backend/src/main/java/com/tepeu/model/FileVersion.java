package com.tepeu.model;

import java.time.LocalDateTime;

public class FileVersion {
    private String id;
    private String workspaceId;
    private String filePath;
    private Integer versionNo;
    private String contentRef;       // 文件系统中内容引用
    private String createdBySession;
    private LocalDateTime createdAt;

    public FileVersion() {}

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getContentRef() { return contentRef; }
    public void setContentRef(String contentRef) { this.contentRef = contentRef; }
    public String getCreatedBySession() { return createdBySession; }
    public void setCreatedBySession(String createdBySession) { this.createdBySession = createdBySession; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
