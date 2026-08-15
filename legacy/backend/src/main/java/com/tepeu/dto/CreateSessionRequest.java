package com.tepeu.dto;

/**
 * Request body for POST /api/session. {@code title} is optional — the caller may name the session
 * up front or leave it null and set it later (e.g. derived from the first user message).
 */
public class CreateSessionRequest {
    private String workspaceId;
    private String title;

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
