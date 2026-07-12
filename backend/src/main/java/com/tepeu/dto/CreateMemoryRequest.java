package com.tepeu.dto;

import java.util.List;

/**
 * Request body for POST /api/memory.
 * Typed DTO so malformed payloads yield a clean 400 instead of a ClassCastException/500.
 */
public class CreateMemoryRequest {
    private String workspaceId;
    private String content;
    private String source;
    private List<String> tags;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
