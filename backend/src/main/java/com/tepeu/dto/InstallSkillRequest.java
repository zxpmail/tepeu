package com.tepeu.dto;

/**
 * 安装技能请求 — content 粘贴 / url 远程 / 二选一（zip 走独立上传接口）。
 */
public class InstallSkillRequest {
    private String workspaceId;
    private String name;
    private String content;
    private String url;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
