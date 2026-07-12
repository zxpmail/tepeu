package com.tepeu.dto;

import java.util.List;

public class MemorySearchRequest {
    private String workspaceId;
    private String query;
    private Integer limit = 20;
    private String cursor;       // cursor-based pagination (created_at of last item)
    private List<String> tags;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
    public String getCursor() { return cursor; }
    public void setCursor(String cursor) { this.cursor = cursor; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
