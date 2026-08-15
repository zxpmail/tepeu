package com.tepeu.dto;

import java.util.List;

/**
 * Request body for PUT /api/memory/{id}.
 * Typed DTO for clean 400s on malformed payloads.
 */
public class UpdateMemoryRequest {
    private String content;
    private List<String> tags;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
